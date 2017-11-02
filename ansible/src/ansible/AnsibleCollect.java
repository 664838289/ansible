package ansible;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// @Produces表示这个接口响应格式为json
public class AnsibleCollect {

    private final String success = "| SUCCESS | rc=0 >>";

    private final String failed = "| FAILED | rc=";

    private final String unreachable = "| UNREACHABLE! =>";

    private Map<String, JSONObject> localAllProcNameMap = new HashMap<String, JSONObject>();

    private Map<String, JSONArray> containerNetStatMap = new HashMap<String, JSONArray>();

    private Map<String, JSONObject> containerInfoMap = new HashMap<String, JSONObject>();

    private Map<String, JSONObject> localPortPid = new HashMap<String, JSONObject>();

    /**
     * 使用默认inventory_file
     * 
     * @param target
     * @return
     */
    public String getNetstat(String target) {
        return getNetstat(null, target);
    }

    /**
     * 返回Json格式 {"ip/hostname":{"exitFlag":"SUCCESS", "netstat":[[proto,local_ip,local_port,peer_ip,peer_port,state],
     * [...], ...]} ,...}
     * 
     * @param inventory_file
     * @param target
     * @return
     */
    @SuppressWarnings("rawtypes")
    public String getNetstat(String inventory_file, String target) {
        String args = "netstat -antp |egrep \"LISTEN|ESTABLISHED\"";
        String results = AnsibleCaller.getInstance().ansibleCmd(inventory_file, target, AnsibleModules.SHELL, args);
        JSONObject jResult = new JSONObject();
        JSONObject jNetstat = new JSONObject();
        JSONObject netNode = null;
        JSONArray netstatList = null;
        JSONObject port = null;
        JSONArray netstatArray;
        String remoteIp, localPort, localPid, procName;
        JSONObject localPortPid = null;
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
                    localPortPid = new JSONObject();
                    netNode.put("exitFlag", line.split(" ")[2]);
                    netNode.put("netstatList", netstatList);
                    netNode.put("port", port);
                    netNode.put("localPortPid", localPortPid);
                    // target机器ip
                    jNetstat.put(line.split(" ")[0], netNode);
                    continue;
                }
                // [proto,local_ip,local_port,peer_ip,peer_port,state,pid/procName]
                list = StringUtil.removeSpaceStr(line.split(" "));
                if (list.size() < 7) {
                    continue;
                }
                if (list.get(5).contains("LISTEN")) {
                    localPid = list.get(6).split("/")[0];
                    port.put(list.get(3).substring(list.get(3).lastIndexOf(":") + 1), localPid);
                }
                else if (list.get(5).contains("ESTABLISHED")) {
                    netstatArray = new JSONArray();
                    localPort = list.get(3).substring(list.get(3).lastIndexOf(":") + 1);
                    netstatArray.put(localPort);
                    remoteIp = list.get(4).substring(0, list.get(4).lastIndexOf(":"));
                    netstatArray.put(remoteIp);
                    netstatArray.put(list.get(4).substring(list.get(4).lastIndexOf(":") + 1));
                    localPid = list.get(6).split("/")[0];
                    netstatArray.put(localPid);
                    netstatList.put(netstatArray);
                    if ("::1".equals(remoteIp) || "127.0.0.1".equals(remoteIp)) {
                        localPortPid.put(localPort, localPid);
                    }
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
            Iterator iterator = jNetstat.keys();
            String key;
            JSONObject netstatObj;
            JSONArray jArray;
            JSONArray jList;
            JSONArray jTemp;
            // 循环解析多个机器的NetStat
            while (iterator.hasNext()) {
                // key 机器IP
                key = (String) iterator.next();
                netstatObj = jNetstat.getJSONObject(key);
                if (!"SUCCESS".equals(netstatObj.getString("exitFlag"))) {
                    netstatObj.remove("netstatList");
                    netstatObj.remove("port");
                    jResult.put(key, netstatObj);
                    continue;
                }
                jList = new JSONArray();
                jTemp = new JSONArray();
                port = netstatObj.getJSONObject("port");
                localPortPid = netstatObj.getJSONObject("localPortPid");
                netstatList = netstatObj.getJSONArray("netstatList");
                JSONObject ContainerInfo = getContainerInfo(key, false);
                JSONObject localAllProcNameObj = getLocalAllProcName(key, ContainerInfo);
                if (localAllProcNameObj.has("exitFlag")) {
                    // System.out.println(localAllProcNameObj.toString());
                    continue;
                }
                for (int i = 0; i < netstatList.length(); i++) {
                    jArray = (JSONArray) netstatList.get(i);
                    if (!port.has(jArray.getString(0))) {
                        JSONObject remote = new JSONObject();
                        JSONObject oneConn = new JSONObject();
                        remoteIp = jArray.getString(1);
                        remote.put("ip", remoteIp);
                        remote.put("port", jArray.getString(2));
                        if (ContainerInfo != null && ContainerInfo.has(remoteIp)) {
                            procName = ContainerInfo.getJSONObject(remoteIp).getString("Name");
                        }
                        else if ("::1".equals(remoteIp) || "127.0.0.1".equals(remoteIp)) {
                            procName = localAllProcNameObj.getString(localPortPid.getString(jArray.getString(2)));
                        }
                        else {
                            procName = getProcInfoByPort(remoteIp, jArray.getString(2)).getString("procName");
                        }
                        remote.put("procName", procName);
                        oneConn.put("remoteConns", remote);
                        oneConn.put("isListen", "N");
                        oneConn.put("local_port", jArray.getString(0));
                        try {
                            oneConn.put("procName", localAllProcNameObj.getString(jArray.getString(3)));
                        }
                        catch (JSONException je) {
                            oneConn.put("procName", "unknown");
                        }
                        jList.put(oneConn);
                    }
                    else {
                        jTemp.put(jArray);
                    }
                }
                Iterator iterator2 = port.keys();
                while (iterator2.hasNext()) {
                    String _port = (String) iterator2.next();
                    JSONArray remote = new JSONArray();
                    for (int i = 0; i < jTemp.length(); i++) {
                        jArray = (JSONArray) jTemp.get(i);
                        if (_port.equals(jArray.getString(0))) {
                            JSONObject oneObj = new JSONObject();
                            remoteIp = jArray.getString(1);
                            oneObj.put("ip", remoteIp);
                            oneObj.put("port", jArray.getString(2));
                            if (ContainerInfo.has(remoteIp)) {
                                procName = ContainerInfo.getJSONObject(remoteIp).getString("Name");
                            }
                            else if ("::1".equals(remoteIp) || "127.0.0.1".equals(remoteIp)) {
                                procName = localAllProcNameObj.getString(localPortPid.getString(jArray.getString(2)));
                            }
                            else {
                                procName = getProcInfoByPort(remoteIp, jArray.getString(2)).getString("procName");
                            }
                            oneObj.put("procName", procName);
                            remote.put(oneObj);
                        }
                    }
                    if (remote.length() < 1) {
                        continue;
                    }
                    JSONObject oneConn = new JSONObject();
                    oneConn.put("isListen", "Y");
                    oneConn.put("local_port", _port);
                    oneConn.put("remoteConns", remote);
                    oneConn.put("procName", localAllProcNameObj.getString(port.getString(_port)));
                    jList.put(oneConn);
                }
                netstatObj.remove("port");
                netstatObj.remove("localPortPid");
                netstatObj.remove("netstatList");
                netstatObj.put("Conns", jList);
                jResult.put(key, netstatObj);
            }
            return jResult.toString();
        }
        // shell执行命令结果为空
        return jResult.toString();
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
        JSONArray newConns = new JSONArray();
        jResultObj.put("exitFlag", "SUCCESS");
        jResultObj.put("Conns", newConns);
        JSONObject oneConn, tempConn, remoteConns, localPortObj, remotePortObj, tempPortObj;
        JSONArray netStatArray = hostNetStatObj.getJSONArray("Conns");
        JSONArray localPortArray, remotePortArray, remoteConnsArray, newRemoteConnsArray;
        Map<String, String> flagNMap = new HashMap<String, String>();
        Map<String, String> flagYMap = new HashMap<String, String>();
        for (int i = 0; i < netStatArray.length(); i++) {
            oneConn = netStatArray.getJSONObject(i);
            // 此项为本地连接远程端口的情况
            if ("N".equals(oneConn.getString("isListen"))) {
                remoteConns = oneConn.getJSONObject("remoteConns");
                if (remoteConns.getString("procName") == oneConn.getString("procName")) {
                    // 本地同一进程自连接，也即是docker-proxy的内部映射连接,例如
                    // 172.20.0.1:40088 172.20.0.21:8593 ESTABLISHED 19616/docker-proxy
                    // 40088端口的进程pid:19616,也即是地址为172.20.0.21的容器运行的进程的进程pid,可忽略此项连接
                    continue;
                }
                // 与本地监听端口建立本地连接，可去除，因为isListen为Y的监听端口也会有这个连接数据，除去重复连接数据
                if ("::1".equals(remoteConns.getString("ip")) || "127.0.0.1".equals(remoteConns.getString("ip"))) {
                    continue;
                }
                if (flagNMap.containsKey(remoteConns.getString("ip") + remoteConns.getString("port"))) {
                    // 已经遍历过连接的同一远程端口，跳过
                    continue;
                }
                flagNMap.put(remoteConns.getString("ip") + remoteConns.getString("port"), "1");
                localPortArray = new JSONArray();
                localPortObj = new JSONObject();
                localPortObj.put("local_port", oneConn.getString("local_port"));
                localPortObj.put("procName", oneConn.getString("procName"));
                localPortArray.put(localPortObj);
                for (int j = i + 1; j < netStatArray.length(); j++) {
                    tempConn = netStatArray.getJSONObject(j);
                    if ("N".equals(tempConn.getString("isListen"))) {
                        remoteConns = tempConn.getJSONObject("remoteConns");
                        if (flagNMap.containsKey(remoteConns.getString("ip") + remoteConns.getString("port"))) {
                            localPortObj = new JSONObject();
                            localPortObj.put("local_port", tempConn.getString("local_port"));
                            localPortObj.put("procName", tempConn.getString("procName"));
                            localPortArray.put(localPortObj);
                        }
                    }
                }
                oneConn.remove("local_port");
                oneConn.remove("procName");
                oneConn.put("local_port", localPortArray);
                newConns.put(oneConn);
            }
            else {// 本地监听端口建立的连接
                remoteConnsArray = oneConn.getJSONArray("remoteConns");
                newRemoteConnsArray = new JSONArray();
                for (int k = 0; k < remoteConnsArray.length(); k++) {
                    remotePortObj = remoteConnsArray.getJSONObject(k);
                    if (flagYMap.containsKey(remotePortObj.getString("ip"))) {
                        // 已经遍历过，跳过 remotePortObj.getString("procName")
                        continue;
                    }
                    flagYMap.put(remotePortObj.getString("ip"), "1");
                    tempPortObj = new JSONObject();
                    remotePortArray = new JSONArray();
                    tempPortObj.put("port", remotePortObj.getString("port"));
                    tempPortObj.put("procName", remotePortObj.getString("procName"));
                    remotePortObj.remove("port");
                    remotePortObj.remove("procName");
                    remotePortArray.put(tempPortObj);
                    remotePortObj.put("port", remotePortArray);
                    newRemoteConnsArray.put(remotePortObj);
                    for (int n = k + 1; n < remoteConnsArray.length(); n++) {
                        remotePortObj = remoteConnsArray.getJSONObject(n);
                        if (flagYMap.containsKey(remotePortObj.getString("ip"))) {
                            tempPortObj = new JSONObject();
                            tempPortObj.put("port", remotePortObj.getString("port"));
                            tempPortObj.put("procName", remotePortObj.getString("procName"));
                            remotePortArray.put(tempPortObj);
                        }
                    }
                }
                oneConn.remove("remoteConns");
                oneConn.put("remoteConns", newRemoteConnsArray);
                newConns.put(oneConn);
            }
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
            localPortPid.put(target, error);
            return error;
        }
        if (!"SUCCESS".equals(ret.getJSONObject(target).getString("exitFlag"))) {
            localPortPid.put(target, ret);
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
            JSONObject tempObj = new JSONObject().put("exitFlag", "FAILED").put("output", "Execute shell cmd failed.");
            localAllProcNameMap.put(target, tempObj);
            return tempObj;
        }
        JSONObject ret = AnsibleUtil.shellResultToJson(results);
        List<String> list;
        if (!"SUCCESS".equals(ret.getJSONObject(target).get("exitFlag").toString())) {
            localAllProcNameMap.put(target, ret);
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
        // StringBuilder sb = new StringBuilder();
        // sb.append("netstat -atnp | grep -E 'ESTABLISHED|LISTEN' | awk '{print \\$4,\\$7}'| grep :");
        // sb.append(port).append("|awk '{print \\$2}'|awk -F '/' '{print \\$1}'| head -1");
        // // 获取端口的pid
        // String results = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, sb.toString());
        // JSONObject ret = AnsibleUtil.shellResultToJson(results);
        // if (!ret.has(target)) {
        // error.put("output", "Failed to get shell results for the target host.").put("procName", "unknown");
        // return error;
        // }
        // if (!"SUCCESS".equals(ret.getJSONObject(target).getString("exitFlag"))) {
        // return ret.put("procName", "unknown");
        // }
        // String tempPids = ret.getJSONObject(target).get("output").toString();
        // String[] temPid = tempPids.split(System.getProperty("line.separator"));
        // if (temPid != null && temPid.length > 0 && temPid[0].trim().length() > 0) {
        // pid = temPid[0];
        // StringBuilder sb2 = new StringBuilder();
        // sb2.append("ps -axwwu | grep \"  ").append(pid).append("  \"| grep -v grep");
        // // 获取pid的进程信息
        // String results2 = AnsibleCaller.getInstance()
        // .ansibleCmd(null, target, AnsibleModules.SHELL, sb2.toString());
        // JSONObject ret2 = AnsibleUtil.shellResultToJson(results2);
        // JSONObject hostObj = ret2.getJSONObject(target);
        // String output = hostObj.get("output").toString();
        // if (StringUtil.isNotEmpty(output)) {
        // if (!"SUCCESS".equals(hostObj.getString("exitFlag"))) {
        // return hostObj.toString();
        // }
        // String[] lines = output.split(System.getProperty("line.separator"));
        // List<String> list = StringUtil.removeSpaceStr(lines[0].split(" "));
        // StringBuilder nameSb = new StringBuilder();
        // for (String line : list.subList(10, list.size())) {
        // nameSb.append(line + " ");
        // }
        // String procName = nameSb.toString();
        // String regex =
        // ".*-host-ip \\d+\\.\\d+\\.\\d+\\.\\d+ -host-port \\d+ -container-ip \\d+\\.\\d+\\.\\d+\\.\\d+ -container-port \\d+.*";
        // if (procName.matches(regex)) {
        // JSONObject ContainerInfo = new JSONObject(getContainerInfo(target, false));
        // String containerIp = procName.split("-container-ip ")[1].split(" -container-port")[0];
        // hostObj.put("procName", ContainerInfo.getJSONObject(containerIp).getString("Name"));
        // }
        // else {
        // hostObj.put("procName", procName);
        // }
        // hostObj.remove("output");
        // return hostObj.toString();
        // }
        // }
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
        JSONObject listen = netstat.getJSONObject("listen");
        JSONArray established = netstat.getJSONArray("establish");
        JSONArray jResult = new JSONArray();
        JSONObject tempNode;
        JSONObject oneConn;
        JSONObject remoteConn;
        for (int i = 0; i < established.length(); i++) {
            tempNode = established.getJSONObject(i);
            if (!listen.has(tempNode.getString("local_port"))) {
                oneConn = new JSONObject();
                remoteConn = new JSONObject();
                remoteConn.put("ip", tempNode.getString("remote_ip"));
                remoteConn.put("port", tempNode.getString("remote_port"));
                oneConn.put("isListen", "N");
                oneConn.put("local_port", tempNode.getString("local_port"));
                oneConn.put("remoteConns", remoteConn);
                jResult.put(oneConn);
            }
        }
        @SuppressWarnings("rawtypes")
        Iterator iterator = listen.keys();
        while (iterator.hasNext()) {
            String _port = (String) iterator.next();
            oneConn = new JSONObject();
            JSONArray ConnArray = new JSONArray();
            oneConn.put("isListen", "Y");
            oneConn.put("remoteConns", ConnArray);
            oneConn.put("local_port", _port);
            for (int i = 0; i < established.length(); i++) {
                tempNode = established.getJSONObject(i);
                if (_port.equals(tempNode.getString("local_port"))) {
                    remoteConn = new JSONObject();
                    remoteConn.put("ip", tempNode.getString("remote_ip"));
                    remoteConn.put("port", tempNode.getString("remote_port"));
                    ConnArray.put(remoteConn);
                }
            }
            if (ConnArray.length() < 1) {
                continue;
            }
            jResult.put(oneConn);
        }
        containerNetStatMap.put(target + "/" + pid, jResult);
        return jResult;
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
            containerInfoMap.put(target, ret);
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
                if (netstatFlag == true) {
                    oneContainer
                        .put("Netstat", getContainerNetstat(target, String.valueOf(oneContainer.getInt("Pid"))));
                }
                jResult.put(IPAddress, oneContainer);
            }
        }
        // List<String> list;
        // String cmd2 = "ps -ef|grep 'docker-proxy -proto tcp'|grep -v grep";
        // String results2 = AnsibleCaller.getInstance().ansibleCmd(null, target, AnsibleModules.SHELL, cmd2);
        // JSONObject ret2 = AnsibleUtil.shellResultToJson(results2);
        // String outputStr2 = ret2.getJSONObject(target).get("output").toString();
        // if (StringUtil.isNotEmpty(outputStr2)) {
        // String[] lines = outputStr.split(System.getProperty("line.separator"));
        // for (String line : lines) {
        // if (StringUtil.isEmpty(line)) {
        // continue;
        // }
        // oneContainer = new JSONObject();
        // list = StringUtil.removeSpaceStr(line.split(" "));
        // JSONObject map = new JSONObject().put("Host_port", list.get(13)).put("Container_port", list.get(17));
        // jResult.getJSONObject(list.get(15)).getJSONArray("Port_map").put(map);
        // }
        // }
        containerInfoMap.put(target, jResult);
        return jResult;
    }

    public String getAllHostStat() {
        String target[] = {
            "10.45.80.1", "10.45.80.2", "10.45.80.3", "10.45.80.19", "10.45.80.21", "10.45.80.22", "10.45.80.23",
            "10.45.80.24", "10.45.80.25", "10.45.80.26", "10.45.80.27", "10.45.80.40", "10.45.80.41", "10.45.80.42",
            "10.45.80.43", "10.45.80.44"
        };
        JSONObject results = new JSONObject();
        for (int i = 0; i < target.length; i++) {
            NetstatRunnable oneRun = new NetstatRunnable(target[i]);
            oneRun.start();
            try {
                oneRun.join();
            }
            catch (InterruptedException e) {
                System.out.println(e.toString());
                e.printStackTrace();
            }
        }

        // NetstatRunnable threadArray[] = new NetstatRunnable[target.length];
        // for (int i = 0; i < threadArray.length; i++) {
        // threadArray[i] = new NetstatRunnable(target[i]);
        // threadArray[i].start();
        // }
        // try {
        // for (int j = 0; j < threadArray.length; j++) {
        // threadArray[j].join();
        // }
        // for (int k = 0; k < threadArray.length; k++) {
        // results.put(threadArray[k].getTarget(), threadArray[k].getResult());
        // }
        // }
        // catch (InterruptedException e) {
        // System.out.println(e.toString());
        // e.printStackTrace();
        // }
        return results.toString();
    }

    class NetstatRunnable extends Thread {

        private String target;

        private JSONObject result;

        public NetstatRunnable(String target) {
            this.target = target;
        }

        public String getTarget() {
            return target;
        }

        public JSONObject getResult() {
            return result;
        }

        @Override
        public void run() {
            String jsonStr = getNetstat(null, target);
            result = mergeNetStatPort(jsonStr, target);
        }

    }

    public static void main(String[] args) {
        // 10.45.80.27 | SUCCESS | rc=0 >>
        // tcp6 0 0 10.45.80.27:35678 10.45.80.26:gap ESTABLISHED
        // 10.45.80.26 | UNREACHABLE! => {...}
        // 10.45.80.27 | FAILED | rc=127 >>
        // linux下测试
        // java -classpath ansible.jar:json-20140107.jar ansible.AnsibleCollect
        if (args.length == 0) {
            String result = new AnsibleCollect().getNetstat("10.45.80.26");
            System.out.println(result);
        }
        else if (args.length == 1) {
            System.out.println(new AnsibleCollect().getAllHostStat());
        }

        // if (args.length == 0) {
        // System.out.println(new AnsibleCollect().getContainerInfo("10.45.80.26"));
        // }
        // else if (args.length == 1) {
        // System.out.println(new AnsibleCollect().getProcInfoByPort("10.45.80.26", args[0]));
        // }
        // else if (args.length == 2) {
        // System.out.println(new AnsibleCollect().getProcInfoByPort(args[0], args[1]));
        // }
        // System.out.println(new AnsibleCollect().getContainerNetstat("10.45.80.26", "2743"));
    }
}
