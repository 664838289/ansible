package ansible;

import org.json.JSONArray;
import org.json.JSONObject;

/*
 * 用于获取docker容器的netstat的数据
 */
public class DockerNetstatNew {
    /**
     * TCP_ESTABLISHED = 1, TCP_SYN_SENT = 2, TCP_SYN_RECV = 3, TCP_FIN_WAIT1 = 4, TCP_FIN_WAIT2 = 5, TCP_TIME_WAIT
     * =6,TCP_CLOSE = 7, TCP_CLOSE_WAIT = 8, TCP_LAST_ACK = 9, TCP_LISTEN = 10, TCP_CLOSING = 11,unix_LISTENING
     * =01,unix_CONNECTED = 03,
     **/
    int st;

    String state = null;

    int local_address0, local_address1, local_address2, local_address3;

    int rem_address0, rem_address1, rem_address2, rem_address3;

    String local_address = "0.0.0.0", rem_address = "0.0.0.0";

    int local_port, rem_port;

    int tx_queue, rx_queue;

    int tr;

    int tm_when;

    int retrnsmt;

    int uid, timeout, inode;

    int Num;

    int RefCount;

    int Flags;

    String Path;

    public DockerNetstatNew() {
    }

    private void parseSocket(String line) { // parse socket entries
        // get the local address in two charecter chunks,convert them from hex to an int
        local_address0 = Integer.valueOf(line.substring(6, 8).trim(), 16).intValue();
        local_address1 = Integer.valueOf(line.substring(8, 10).trim(), 16).intValue();
        local_address2 = Integer.valueOf(line.substring(10, 12).trim(), 16).intValue();
        local_address3 = Integer.valueOf(line.substring(12, 14).trim(), 16).intValue();
        // then reverse them into a human readable string
        local_address = local_address3 + "." + local_address2 + "." + local_address1 + "." + local_address0;
        // get the local port and convert from hex
        local_port = Integer.valueOf(line.substring(15, 19).trim(), 16).intValue();
        // same as above except for the remoe addresses
        rem_address0 = Integer.valueOf(line.substring(20, 22).trim(), 16).intValue();
        rem_address1 = Integer.valueOf(line.substring(22, 24).trim(), 16).intValue();
        rem_address2 = Integer.valueOf(line.substring(24, 26).trim(), 16).intValue();
        rem_address3 = Integer.valueOf(line.substring(26, 28).trim(), 16).intValue();
        rem_address = rem_address3 + "." + rem_address2 + "." + rem_address1 + "." + rem_address0;
        // remote port
        rem_port = Integer.valueOf(line.substring(29, 33).trim(), 16).intValue();
        // get the state number and convert to int from hex
        st = Integer.valueOf(line.substring(34, 36).trim(), 16).intValue();
        // based on the state number set the state string //can't find string for UDP or RAW
        state = getState(st);
        // uid = Integer.valueOf(line.substring(75, 81).trim()).intValue(); // get the user id number
        // timeout = Integer.valueOf(line.substring(82, 90).trim()).intValue(); // get the timout value
    }

    private String getState(int st) {
        String state = null;
        switch (st) {
            case 0:
                state = null;
                break;
            case 1:
                state = "ESTABLISHED";
                break;
            case 2:
                state = "SYN_SENT";
                break;
            case 3:
                state = "SYN_RECV";
                break;
            case 4:
                state = "FIN_WAIT1";
                break;
            case 5:
                state = "FIN_WAIT2";
                break;
            case 6:
                state = "TIME_WAIT";
                break;
            case 7:
                state = "CLOSE";
                break;
            case 8:
                state = "CLOSE_WAIT";
                break;
            case 9:
                state = "LAST_ACK";
                break;
            case 10:
                state = "LISTEN";
                break;
            case 11:
                state = "CLOSING";
                break;

        }
        return state;
    }

    /*
     * 转换十六进制ipv6字符串为IPv6或IPv4地址
     */
    private static String convertHex2IP(String key) {
        String IPV6Address = "";
        String IPAddress = "";
        String strKey = "";
        String ip1 = key.substring(0, 24);
        String tIP1 = ip1.replace("0000", "").trim();
        if (!"".equals(tIP1) && !"FFFF".equals(tIP1)) {
            // 将ip按：分隔
            while (!"".equals(key)) {
                strKey = key.substring(0, 4);
                key = key.substring(4);
                if ("".equals(IPV6Address)) {
                    IPV6Address = strKey;
                }
                else {
                    IPV6Address += ":" + strKey;
                }
            }
            IPAddress = IPV6Address;
        }
        else {
            int address0 = Integer.valueOf(key.substring(24, 26).trim(), 16).intValue();
            int address1 = Integer.valueOf(key.substring(26, 28).trim(), 16).intValue();
            int address2 = Integer.valueOf(key.substring(28, 30).trim(), 16).intValue();
            int address3 = Integer.valueOf(key.substring(30, 32).trim(), 16).intValue();
            if (address0 == 1 && address1 == 0 && address2 == 0 && address3 == 0) {
                IPAddress = "::1";
            }
            else {
                IPAddress = address3 + "." + address2 + "." + address1 + "." + address0;
            }
        }
        return IPAddress;
    }

    private void parseSocketTcp6(String line) { // parse socket entries
        // get the local address in two charecter chunks,convert them from hex to an int
        // System.out.println("local: " + line.substring(6, 38) + "  port: " + line.substring(39, 43));
        // System.out.println("remote: " + line.substring(44, 76) + "  port: " + line.substring(77, 81));
        // System.out.println("state: " + line.substring(82, 84));
        // System.out.println("uid: " + line.substring(123, 129) + "  timeout: " + line.substring(130, 132));
        // then reverse them into a human readable string
        local_address = convertHex2IP(line.substring(6, 38).trim());
        // get the local port and convert from hex
        local_port = Integer.valueOf(line.substring(39, 43).trim(), 16).intValue();
        // same as above except for the remoe addresses
        rem_address = convertHex2IP(line.substring(44, 76).trim());
        // remote port
        rem_port = Integer.valueOf(line.substring(77, 81).trim(), 16).intValue();
        // get the state number and convert to int from hex
        st = Integer.valueOf(line.substring(82, 84).trim(), 16).intValue();
        // based on the state number set the state string //can't find string for UDP or RAW
        state = getState(st);
        // uid = Integer.valueOf(line.substring(123, 129).trim()).intValue(); // get the user id number
        // timeout = Integer.valueOf(line.substring(130, 138).trim()).intValue(); // get the timout value
    }

    // this searches through the networking tables and determines the type by which table it's in
    // it also fills up the information on a socket by passing it's table entry to the proper parser
    // this searches by inode number
    public JSONObject update(String tcpStr, String tcp6Str) {
        JSONObject netstat = new JSONObject();
        JSONObject listenPort = new JSONObject();
        JSONArray establishConn = new JSONArray();
        JSONArray jList = new JSONArray();
        String container_IP = null;
        // netstat.put("listen", listenPort);
        // netstat.put("establish", establishConn);
        try {
            String[] lines = tcpStr.split(System.getProperty("line.separator"));
            // System.out.println("\tuid \ttimeout \tlocal address \t\tremote address \tstate");
            for (String line : lines) { // keep reading until something causes us to jump out EOF or found inode
                try {
                    if (line.isEmpty() || line.contains("local_address")) {
                        continue;
                    }
                    // check o see if its inode matches our own, trimming and converting
                    parseSocket(line);// send the line to be parsed
                    if ("ESTABLISHED".equals(state)) {
                        JSONObject oneConn = new JSONObject();
                        oneConn.put("local_ip", local_address).put("local_port", String.valueOf(local_port))
                            .put("remote_ip", rem_address).put("remote_port", String.valueOf(rem_port));
                        establishConn.put(oneConn);
                    }
                    else if ("LISTEN".equals(state)) {
                        listenPort.put(String.valueOf(local_port), local_address);
                    }
                    // System.out.println("\t" + uid + "\t" + timeout + "\t" + local_address + ":" + local_port
                    // + "\t\t" + rem_address + ":" + rem_port + "\t" + state); // print a debug line
                }
                catch (NumberFormatException nfe) {
                    // the inode we read in wasn't a number, must not be an inode
                    System.out.println("some datas we read in wasn't a number.[" + nfe.toString() + "]");
                }
            }
            lines = tcp6Str.split(System.getProperty("line.separator"));
            for (String line : lines) {
                try {
                    if (line.isEmpty() || line.contains("local_address")) {
                        continue;
                    }
                    parseSocketTcp6(line);
                    if ("ESTABLISHED".equals(state)) {
                        JSONObject oneConn = new JSONObject();
                        oneConn.put("local_ip", local_address).put("local_port", String.valueOf(local_port))
                            .put("remote_ip", rem_address).put("remote_port", String.valueOf(rem_port));
                        establishConn.put(oneConn);
                    }
                    else if ("LISTEN".equals(state)) {
                        listenPort.put(String.valueOf(local_port), local_address);
                    }
                }
                catch (NumberFormatException nfe) {
                    System.out.println("some datas we read in wasn't a number.[" + nfe.toString() + "]");
                }
            }
            // 处理一下格式
            JSONObject oneConn, tempObj;
            for (int i = 0; i < establishConn.length(); i++) {
                tempObj = establishConn.getJSONObject(i);
                JSONObject remote = new JSONObject();
                oneConn = new JSONObject();
                remote.put("ip", tempObj.getString("remote_ip"));
                remote.put("port", tempObj.getString("remote_port"));
                oneConn.put("remoteConns", remote);
                oneConn.put("local_port", tempObj.getString("local_port"));
                if (listenPort.has(tempObj.getString("local_port"))) {
                    oneConn.put("isListen", true);
                }
                else {
                    oneConn.put("isListen", false);
                }
                jList.put(oneConn);
                if (container_IP == null && !"::1,127.0.0.1".contains(tempObj.getString("local_ip"))) {
                    container_IP = tempObj.getString("local_ip");
                    netstat.put("container_ip", container_IP);
                }
            }
        }
        catch (NullPointerException npe) {
            System.out.println(npe.toString());
        }
        netstat.put("netstat", jList);
        return netstat;
    }

    public static void main(String args[]) {
        // docker inspect -f '{{.State.Pid}} {{.Id}} {{.Name}}' $(docker ps -q)
        // docker inspect -f '{{.State.Pid}} {{.Id}} {{.Name}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
        // $(docker ps -q)
        // $(docker ps -q)
        // $(docker ps -q)
        // cat /proc/25228/net/tcp6
        // docker network inspect bridge
        // ps -ef|grep 'docker-proxy -proto tcp'|grep -v grep
    }
}
