package ansible;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

// @Produces表示这个接口响应格式为json
public class AnsibleCollectNew {

    private final String success = "| SUCCESS | rc=0 >>";

    private final String failed = "| FAILED | rc=";

    private final String unreachable = "| UNREACHABLE! =>";

    private static Map<String, JSONObject> localAllProcNameMap = new HashMap<String, JSONObject>();

    private static Map<String, JSONArray> containerNetStatMap = new HashMap<String, JSONArray>();

    private static Map<String, JSONObject> containerInfoMap = new HashMap<String, JSONObject>();

    private static Map<String, JSONObject> localPortPid = new HashMap<String, JSONObject>();

    private static Map<String, JSONObject> portProcName = new HashMap<String, JSONObject>();

    /**
     * 使用默认inventory_file
     * 
     * @param target
     * @return
     */
    public String getNetstat(String target) {
        return getNetstat(null, target);
    }

    public String getNetstat(String inventory_file, String target) {
        // 获取某个target的本地tcp类型端口与其他机器建立的主动或被动连接的状态信息
        String args = "netstat -antp |egrep \"LISTEN|ESTABLISHED\"";
        String results = AnsibleCaller.getInstance().ansibleCmd(inventory_file, target, "shell", args);
        JSONObject jResult = new JSONObject();
        JSONObject jNetstat = new JSONObject();
        JSONObject netNode = null;
        JSONArray netstatList = null;
        JSONObject port = null;
        JSONArray netstatArray;
        String localIpFliter = "::1,127.0.0.1," + target;
        List<String> list;
        if (StringUtil.isNotEmpty(results)) {
            String[] lines = results.split(System.getProperty("line.separator"));
            for (String line : lines) {
                if (StringUtil.isEmpty(line)) {
                    continue;
                }
                if (line.contains(success) || line.contains(failed) || line.contains(unreachable)) {
                    netNode = new JSONObject();
                    netstatList = new JSONArray();
                    port = new JSONObject();
                    netNode.put("exitFlag", line.split(" ")[2]);
                    netNode.put("netstatList", netstatList);
                    netNode.put("port", port);
                    jNetstat.put(line.split(" ")[0], netNode);
                    continue;
                }
                list = StringUtil.removeSpaceStr(line.split(" "));
                // [proto,local_ip,local_port,peer_ip,peer_port,state]
                if (list.size() < 7) {
                    continue;
                }
                if (list.get(5).contains("LISTEN") && port != null) {
                    port.put(list.get(3).substring(list.get(3).lastIndexOf(":") + 1), "0");
                }
                else if (list.get(5).contains("ESTABLISHED") && netstatList != null) {
                    if (!localIpFliter.contains(list.get(3).substring(0, list.get(3).lastIndexOf(":")))) {
                        // 过滤掉一个容器的一个进程中内部的端口互相连接
                        continue;
                    }
                    netstatArray = new JSONArray();
                    netstatArray.put(list.get(3).substring(list.get(3).lastIndexOf(":") + 1));
                    netstatArray.put(list.get(4).substring(0, list.get(4).lastIndexOf(":")));
                    netstatArray.put(list.get(4).substring(list.get(4).lastIndexOf(":") + 1));
                    netstatList.put(netstatArray);
                }
                else {
                    if (netNode != null && netNode.has("output")) {
                        netNode.put("output", netNode.getString("output") + line);
                    }
                    else if (netNode != null) {
                        netNode.put("output", line);
                    }
                }
            }
            @SuppressWarnings("rawtypes")
            Iterator iterator = jNetstat.keys();
            String key;
            JSONObject netstatObj;
            JSONArray jArray;
            JSONArray jList;
            while (iterator.hasNext()) {
                key = (String) iterator.next();
                netstatObj = jNetstat.getJSONObject(key);
                if (!"SUCCESS".equals(netstatObj.getString("exitFlag"))) {
                    netstatObj.remove("netstatList");
                    netstatObj.remove("port");
                    jResult.put(key, netstatObj);
                    continue;
                }
                jList = new JSONArray();
                port = netstatObj.getJSONObject("port");
                netstatList = netstatObj.getJSONArray("netstatList");
                for (int i = 0; i < netstatList.length(); i++) {
                    jArray = (JSONArray) netstatList.get(i);
                    JSONObject remote = new JSONObject();
                    JSONObject oneConn = new JSONObject();
                    remote.put("ip", jArray.getString(1));
                    remote.put("port", jArray.getString(2));
                    oneConn.put("remoteConns", remote);
                    oneConn.put("local_port", jArray.getString(0));
                    if (port.has(jArray.getString(0))) {
                        oneConn.put("isListen", true);
                    }
                    else {
                        oneConn.put("isListen", false);
                    }
                    jList.put(oneConn);
                }
                netstatObj.remove("port");
                netstatObj.remove("netstatList");
                netstatObj.put("Conns", jList);
                jResult.put(key, netstatObj);
            }
        }
        return jResult.toString();
    }

    public JSONObject mergeNetStatProc(String netStatJsonStr, String target) {
        JSONObject netStatObj = new JSONObject(netStatJsonStr);
        if (!netStatObj.has(target)) {
            return new JSONObject().put("exitFlag", "FAILED").put("output", "source netstat data is null.");
        }
        JSONObject hostNetStatObj = netStatObj.getJSONObject(target);
        if (!"SUCCESS".equals(hostNetStatObj.getString("exitFlag"))) {
            return hostNetStatObj;
        }
        JSONObject jResultObj = new JSONObject();
        JSONArray newConns = new JSONArray();
        JSONObject oneConn, tempConn, remoteConns, oneProcConn, connObj, connObjs;
        String remoteIP, local_procName, remote_procName;
        JSONArray connArray;
        jResultObj.put("exitFlag", "SUCCESS");
        jResultObj.put("Conns", newConns);
        JSONArray netStatArray = hostNetStatObj.getJSONArray("Conns");
        JSONObject ContainerInfo = getContainerInfo(target, false);
        for (int i = 0; i < netStatArray.length(); i++) {
            oneConn = netStatArray.getJSONObject(i);
            remoteConns = oneConn.getJSONObject("remoteConns");
            remoteIP = remoteConns.getString("ip");
            oneConn.put("procName", getProcName(target, oneConn.getString("local_port")));
            if (ContainerInfo != null && ContainerInfo.has(remoteIP)) {
                remoteConns.put("procName", ContainerInfo.getJSONObject(remoteIP).getString("Name"));
            }
            else {
                if ("::1,127.0.0.1".contains(remoteIP)) {
                    remoteConns.put("procName", getProcName(target, remoteConns.getString("port")));
                }
                else {
                    remoteConns.put("procName", getProcName(remoteIP, remoteConns.getString("port")));
                }
            }
        }
        JSONObject procNameFlag = new JSONObject();
        for (int j = 0; j < netStatArray.length(); j++) {
            oneConn = netStatArray.getJSONObject(j);
            if (procNameFlag.has(oneConn.getString("procName"))) {
                continue;
            }
            local_procName = oneConn.getString("procName");
            procNameFlag.put(local_procName, true);
            oneProcConn = new JSONObject();
            connArray = new JSONArray();
            connObjs = new JSONObject();
            oneProcConn.put("procName", local_procName);
            oneProcConn.put("conn_proc", connObjs);
            for (int k = j; k < netStatArray.length(); k++) {
                tempConn = netStatArray.getJSONObject(k);
                if (local_procName == tempConn.getString("procName")) {
                    connObj = new JSONObject();
                    connObj.put("remoteConns", tempConn.getJSONObject("remoteConns"));
                    connObj.put("local_port", tempConn.getString("local_port"));
                    connObj.put("isListen", tempConn.getBoolean("isListen"));
                    connArray.put(connObj);
                }
            }
            JSONArray tempobj;
            JSONObject oneObj, oneProcObj;
            JSONObject nameFlag = new JSONObject();
            for (int m = 0; m < connArray.length(); m++) {
                tempConn = connArray.getJSONObject(m);
                remoteConns = tempConn.getJSONObject("remoteConns");
                remoteIP = remoteConns.getString("ip");
                remote_procName = remoteConns.getString("procName");
                oneObj = new JSONObject();
                oneObj.put("local_port", tempConn.getString("local_port"));
                oneObj.put("remote_port", remoteConns.getString("port"));
                oneObj.put("isListen", tempConn.getBoolean("isListen"));
                if (connObjs.has(remoteIP)) {
                    tempobj = connObjs.getJSONArray(remoteIP);
                    if (nameFlag.has(remoteIP + remote_procName)) {
                        oneProcObj = tempobj.getJSONObject(nameFlag.getInt(remoteIP + remote_procName));
                        oneProcObj.getJSONArray("procPorts").put(oneObj);
                    }
                    else {
                        oneProcObj = new JSONObject();
                        oneProcObj.put("procName", remote_procName);
                        oneProcObj.put("procPorts", new JSONArray().put(oneObj));
                        nameFlag.put(remoteIP + remote_procName, tempobj.length());
                        tempobj.put(oneProcObj);
                    }
                }
                else {
                    tempobj = new JSONArray();
                    oneProcObj = new JSONObject();
                    oneProcObj.put("procName", remote_procName);
                    oneProcObj.put("procPorts", new JSONArray().put(oneObj));
                    nameFlag.put(remoteIP + remote_procName, 0);
                    tempobj.put(oneProcObj);
                    connObjs.put(remoteIP, tempobj);
                }
            }
            newConns.put(oneProcConn);
        }
        return jResultObj;
    }

    /**
     * 合并一个目标主机的采集的getNetstat数据,将IP地址相同的连接合并
     * 
     * @param netStatJsonStr
     * @param target
     * @return
     */
    public JSONObject mergeNetStatPort(String netStatJsonStr, String target) {
        JSONObject netStatObj = new JSONObject(netStatJsonStr);
        if (!netStatObj.has(target)) {
            return new JSONObject().put("exitFlag", "FAILED").put("output", "source netstat data is null.");
        }
        JSONObject hostNetStatObj = netStatObj.getJSONObject(target);
        if (!"SUCCESS".equals(hostNetStatObj.getString("exitFlag"))) {
            return hostNetStatObj;
        }
        JSONObject jResultObj = new JSONObject();
        JSONObject newConns = new JSONObject();
        jResultObj.put("exitFlag", "SUCCESS");
        jResultObj.put("Conns", newConns);
        JSONObject oneConn, tempConn, remoteConns, oneIPConn, portObj;
        JSONArray netStatArray = hostNetStatObj.getJSONArray("Conns");
        JSONArray connArray;
        JSONObject remoteIPMap = new JSONObject();
        String remoteIP, localPort, remotePort, local_procName, remote_procName;
        JSONObject ContainerInfo = getContainerInfo(target, false);
        for (int i = 0; i < netStatArray.length(); i++) {
            oneConn = netStatArray.getJSONObject(i);
            remoteConns = oneConn.getJSONObject("remoteConns");
            remoteIP = remoteConns.getString("ip");
            if (remoteIPMap.has(remoteIP)) {
                continue;
            }
            oneIPConn = new JSONObject();
            connArray = new JSONArray();
            if (ContainerInfo != null && ContainerInfo.has(remoteIP)) {
                oneIPConn.put("isContainer", true);
                oneIPConn.put("host_ip", target);
                remoteIPMap.put(remoteIP, target);
            }
            else {
                oneIPConn.put("isContainer", false);
                if ("::1,127.0.0.1".contains(remoteIP)) {
                    remoteIPMap.put(remoteIP, target);
                }
                else {
                    remoteIPMap.put(remoteIP, remoteIP);
                }
            }
            oneIPConn.put("ports", connArray);
            for (int j = i; j < netStatArray.length(); j++) {
                tempConn = netStatArray.getJSONObject(j);
                remoteConns = tempConn.getJSONObject("remoteConns");
                if (remoteIP.equals(remoteConns.getString("ip"))) {
                    localPort = tempConn.getString("local_port");
                    remotePort = remoteConns.getString("port");
                    remote_procName = getProcName(remoteIPMap.getString(remoteIP), remotePort);
                    local_procName = getProcName(target, localPort);
                    portObj = new JSONObject().put("local_port", tempConn.getString("local_port"));
                    portObj.put("local_procName", local_procName);
                    portObj.put("isListen", tempConn.getBoolean("isListen"));
                    portObj.put("remote_port", remoteConns.getString("port"));
                    portObj.put("remote_procName", remote_procName);
                    connArray.put(portObj);
                }
            }
            newConns.put(remoteIP, oneIPConn);
        }
        return jResultObj;
    }

    private JSONObject getLocalPortPid(String target) {
        if (localPortPid.containsKey(target)) {
            return localPortPid.get(target);
        }
        String args = "netstat -antp |egrep \"LISTEN|ESTABLISHED\"";
        String results = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, args);
        JSONObject ret = AnsibleUtil.shellResultToJson(results);
        if (!ret.has(target)) {
            JSONObject error = new JSONObject();
            error.put("output", "Failed to get shell results for the target host.").put("exitFlag", "FAILED");
            return error;
        }
        if (!"SUCCESS".equals(ret.getJSONObject(target).getString("exitFlag"))) {
            return ret;
        }
        String output = ret.getJSONObject(target).get("output").toString();
        String[] lines = output.split(System.getProperty("line.separator"));
        JSONObject portPidObj = new JSONObject();
        List<String> list;
        String localPort, localPid;
        for (String line : lines) {
            if (StringUtil.isEmpty(line)) {
                continue;
            }
            list = StringUtil.removeSpaceStr(line.split(" "));
            if (list.size() < 7) {
                continue;
            }
            localPort = list.get(3).substring(list.get(3).lastIndexOf(":") + 1);
            localPid = list.get(6).split("/")[0];
            portPidObj.put(localPort, localPid);
        }
        localPortPid.put(target, portPidObj);
        return portPidObj;
    }

    public JSONObject getLocalAllProcName(String target, JSONObject ContainerInfo) {
        if (localAllProcNameMap.containsKey(target)) {
            return localAllProcNameMap.get(target);
        }
        String cmd = "ps -axwwu";
        String results = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, cmd);
        if (StringUtil.isEmpty(results)) {
            return new JSONObject().put("exitFlag", "FAILED").put("output", "Execute shell cmd failed.");
        }
        JSONObject ret = AnsibleUtil.shellResultToJson(results);
        List<String> list;
        if (!"SUCCESS".equals(ret.getJSONObject(target).get("exitFlag").toString())) {
            return ret;
        }
        if (ContainerInfo == null) {
            ContainerInfo = getContainerInfo(target, false);
        }
        String output = ret.getJSONObject(target).get("output").toString();
        String[] lines = output.split(System.getProperty("line.separator"));
        JSONObject procNameObj = new JSONObject();
        for (String line : lines) {
            if (StringUtil.isEmpty(line)) {
                continue;
            }
            list = StringUtil.removeSpaceStr(line.split(" "));
            if (list.size() < 10) {
                continue;
            }
            StringBuilder nameSb = new StringBuilder();
            for (String L : list.subList(10, list.size())) {
                nameSb.append(L).append(" ");
            }
            String procName = nameSb.toString();
            String regex = ".*-host-ip \\d+\\.\\d+\\.\\d+\\.\\d+ -host-port \\d+ -container-ip \\d+\\.\\d+\\.\\d+\\.\\d+ -container-port \\d+.*";
            if (procName.matches(regex)) {
                String containerIp = procName.split("-container-ip ")[1].split(" -container-port")[0];
                if (ContainerInfo == null) {
                    procName = "unknown";
                }
                else {
                    procName = ContainerInfo.getJSONObject(containerIp).getString("Name");
                }
            }
            procNameObj.put(list.get(1), procName);
        }
        localAllProcNameMap.put(target, procNameObj);
        return procNameObj;
    }

    public String getProcName(String target, String port) {
        String procName;
        if (portProcName.containsKey(target + "/" + port)) {
            Long time = portProcName.get(target + "/" + port).getLong("time");
            // 如果缓存数据超过一定时间，则更新一遍数据
            if ((new Date().getTime() - time) > 1000 * 60 * 5) {
                procName = getProcInfoByPort(target, port).getString("procName");
                portProcName.put(target + "/" + port,
                    new JSONObject().put("procName", procName).put("time", new Date().getTime()));
            }
            else {
                procName = portProcName.get(target + "/" + port).getString("procName");
            }
        }
        else {
            procName = getProcInfoByPort(target, port).getString("procName");
            portProcName.put(target + "/" + port,
                new JSONObject().put("procName", procName).put("time", new Date().getTime()));
        }
        return procName;
    }

    public JSONObject getProcInfoByPort(String target, String port) {
        // ps -axwwu | grep `netstat -atnp | grep ESTABLISHED | awk '{print $4,$7}'|
        // grep 22|awk '{print $2}'|awk -F '/' '{print $1}'|head -1` | grep -v grep
        String pid;
        JSONObject error = new JSONObject();
        error.put("exitFlag", "FAILED");
        JSONObject portPidObj = getLocalPortPid(target);
        if (portPidObj.has(port)) {
            pid = portPidObj.getString(port);
            JSONObject localAllProcNameObj = getLocalAllProcName(target, getContainerInfo(target, false));
            if (localAllProcNameObj.has("exitFlag") || !localAllProcNameObj.has(pid)) {
                // System.out.println(localAllProcNameObj.toString());
                error.put("output", "Failed to get all process name by the target host.").put("procName", "unknown");
                return error;
            }
            String procName = localAllProcNameObj.getString(pid);
            return new JSONObject().put("exitFlag", "SUCCESS").put("procName", procName);
        }
        else {
            JSONObject ContainerInfo = getContainerInfo(target, false);
            if (ContainerInfo == null || ContainerInfo.has("exitFlag")) {
                error.put("output", "Failed to get process name.").put("procName", "unknown");
                return error;
            }
            @SuppressWarnings("rawtypes")
            Iterator iterator = ContainerInfo.keys();
            JSONArray netstat;
            while (iterator.hasNext()) {
                String containerIP = (String) iterator.next();
                if (!ContainerInfo.getJSONObject(containerIP).has("Pid")) {
                    continue;
                }
                netstat = getContainerNetstat(target,
                    String.valueOf(ContainerInfo.getJSONObject(containerIP).getInt("Pid")));
                for (int i = 0; i < netstat.length(); i++) {
                    if (port.equals(netstat.getJSONObject(i).getString("local_port"))) {
                        return new JSONObject().put("exitFlag", "SUCCESS").put("procName",
                            ContainerInfo.getJSONObject(containerIP).getString("Name"));
                    }
                }
            }
        }
        error.put("output", "Failed to get process name.").put("procName", "unknown");
        return error;
    }

    public JSONArray getContainerNetstat(String target, String pid) {
        if (containerNetStatMap.containsKey(target + "/" + pid)) {
            return containerNetStatMap.get(target + "/" + pid);
        }
        String cmd1 = "cat /proc/" + pid + "/net/tcp";
        String cmd2 = "cat /proc/" + pid + "/net/tcp6";
        String results = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, cmd1);
        JSONObject ret = AnsibleUtil.shellResultToJson(results);
        String tcpStr = ret.getJSONObject(target).get("output").toString();
        String results2 = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, cmd2);
        JSONObject ret2 = AnsibleUtil.shellResultToJson(results2);
        String tcp6Str = ret2.getJSONObject(target).get("output").toString();
        JSONObject netstat = new DockerNetstat().update(tcpStr, tcp6Str);
        JSONArray established = netstat.getJSONArray("establish");
        containerNetStatMap.put(target + "/" + pid, established);
        return established;
    }

    public JSONObject getContainerInfo(String target) {
        return getContainerInfo(target, true);
    }

    public JSONObject getContainerInfo(String target, boolean netstatFlag) {
        if (containerInfoMap.containsKey(target)) {
            return containerInfoMap.get(target);
        }
        String cmd = "docker inspect `docker ps -q`";
        JSONObject jResult = new JSONObject();
        String results = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, cmd);
        JSONObject ret = AnsibleUtil.shellResultToJson(results);
        if (ret == null || !ret.has(target)) {
            return null;
        }
        if (!"SUCCESS".equals(ret.getJSONObject(target).getString("exitFlag"))) {
            return ret;
        }
        String outputStr = ret.getJSONObject(target).get("output").toString();
        JSONObject oneContainer;
        JSONObject tempObj;
        JSONObject NetworkSettings;
        String IPAddress = null;
        if (StringUtil.isNotEmpty(outputStr)) {
            JSONArray outputJArray = new JSONArray(outputStr);
            for (int i = 0; i < outputJArray.length(); i++) {
                tempObj = outputJArray.getJSONObject(i);
                NetworkSettings = tempObj.getJSONObject("NetworkSettings");
                if (!NetworkSettings.getString("IPAddress").isEmpty()) {
                    IPAddress = NetworkSettings.getString("IPAddress");
                }
                else if (NetworkSettings.getJSONObject("Networks").length() > 0) {
                    JSONObject Networks = NetworkSettings.getJSONObject("Networks");
                    String key = (String) Networks.keys().next();
                    IPAddress = Networks.getJSONObject(key).getString("IPAddress");
                }
                if (IPAddress == null || IPAddress.isEmpty()) {
                    continue;
                }
                oneContainer = new JSONObject();
                oneContainer.put("Pid", tempObj.getJSONObject("State").getInt("Pid"));
                oneContainer.put("Id", tempObj.getString("Id"));
                oneContainer.put("Name", tempObj.getString("Name").substring(1));
                // oneContainer.put("Ports", NetworkSettings.getJSONObject("Ports"));
                if (netstatFlag) {
                    oneContainer
                        .put("Netstat", getContainerNetstat(target, String.valueOf(oneContainer.getInt("Pid"))));
                }
                jResult.put(IPAddress, oneContainer);
            }
        }
        containerInfoMap.put(target, jResult);
        return jResult;
    }

    public String getAllHostStat() {
        localAllProcNameMap.clear();
        containerNetStatMap.clear();
        containerInfoMap.clear();
        localPortPid.clear();
        String target[] = {
            "10.45.80.25", "10.45.80.26", "10.45.80.27", "10.45.80.41", "10.45.80.42", "10.45.80.43"
        };
        JSONObject results = new JSONObject();
        NetstatRunnable threadArray[] = new NetstatRunnable[target.length];
        for (int i = 0; i < threadArray.length; i++) {
            threadArray[i] = new NetstatRunnable(target[i], 1);
            threadArray[i].start();
        }
        try {
            for (NetstatRunnable runnable : threadArray) {
                runnable.join();
                results.put(runnable.getTarget(), runnable.getResult());
            }
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
        return results.toString();
    }

    class NetstatRunnable extends Thread {

        private String target;

        private JSONObject result;

        private int flag;

        public NetstatRunnable(String target, int flag) {
            this.target = target;
            this.flag = flag;
        }

        public String getTarget() {
            return target;
        }

        public JSONObject getResult() {
            return result;
        }

        @Override
        public void run() {
            switch (flag) {
                case 0:
                    result = mergeNetStatPort(getNetstat(null, target), target);
                    break;
                case 1:
                    result = mergeNetStatProc(getNetstat(null, target), target);
                    break;
            }

        }

    }

    public String captureNetworkPackets(String target, String args) {
        // sysdig -M1 -N -A -c echo_fds disable_color fd.ip=10.45.80.27
        // sysdig -M1 -N -A -c iobytes fd.ip=10.45.80.27
        // sysdig -M1 -N -A -c iobytes fd.lport=9092
        // sysdig -M1 -N -A -c echo_fds disable_color fd.lport=9092
        String regx = "\\d+\\.\\d+\\.\\d+\\.\\d+";
        // ------ Write 132KB to 10.45.16.209:51448->10.45.80.44:22 (sshd)
        // ------ Read 19B from 10.45.80.24:55784->[01;31m[K10.45.80.44[m[K:179 (bird)
        String testRegx = "------ \\S{4,5} \\S+ \\S{2,4} .*" + args;
        // M1 抓取一秒内的包
        String cmd = "sysdig -M1 -N -A -c echo_fds disable_color ";
        String testCmd = cmd + "| grep " + args + "|head";
        if ("::1".equals(args) || "127.0.0.1".equals(args)) {
            cmd = cmd + "fd.ip=" + target;
        }
        else if (args.matches(regx)) {
            cmd = cmd + "fd.ip=" + args;
        }
        else {
            try {
                cmd = cmd + "fd.lport=" + Integer.parseInt(args);
            }
            catch (NumberFormatException e) {
                return e.toString();
            }
        }
        JSONObject ret = new JSONObject();
        String testResults = AnsibleCaller.getInstance().ansibleCmd(null, target, "shell", testCmd);
        // 先测试是否有数据，若有则抓取，否则就不抓取，防止sysdig指令堵塞
        if (Pattern.compile(testRegx).matcher(testResults).find()) {
            String results = AnsibleCaller.getInstance().ansibleCmd(null, target, "shell", cmd);
            ret.put("ret", AnsibleUtil.shellResultToJson(results));
        }
        if (!ret.has("ret") || ret.getJSONObject("ret").length() < 1) {
            return "";
        }
        return ret.getJSONObject("ret").getJSONObject(target).get("output").toString();
    }

    public static void main(String[] args) {
        // 10.45.80.27 | SUCCESS | rc=0 >>
        // tcp6 0 0 10.45.80.27:35678 10.45.80.26:gap ESTABLISHED
        // 10.45.80.26 | UNREACHABLE! => {...}
        // 10.45.80.27 | FAILED | rc=127 >>
        // linux下测试
        // java -classpath ansible.jar:json-20140107.jar ansible.AnsibleCollectNew
        if (args.length == 0) {
            String result = new AnsibleCollectNew().getNetstat("10.45.80.26");
            System.out.println(new AnsibleCollectNew().mergeNetStatPort(result, "10.45.80.26"));
        }
        else if (args.length == 1) {
            File f = new File("/var/output.txt");
            try {
                FileWriter fw = new FileWriter(f);
                fw.write(new AnsibleCollectNew().getAllHostStat());
                fw.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        // System.out.println(new AnsibleCollectNew().getAllHostStat());

        // File f = new File("/var/output.txt");
        // try {
        // FileWriter fw = new FileWriter(f);
        // fw.write(new AnsibleCollectNew().captureNetworkPackets("10.45.80.26", args[0]));
        // fw.close();
        // }
        // catch (IOException e) {
        // e.printStackTrace();
        // }
    }
}
