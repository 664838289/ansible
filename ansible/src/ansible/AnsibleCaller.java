package ansible;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONObject;

public class AnsibleCaller {

    private static AnsibleCaller instance;

    public synchronized static AnsibleCaller getInstance() {
        if (instance == null) {
            instance = new AnsibleCaller();
        }
        return instance;
    }

    // 通过python接口执行ansible命令
    public JSONObject executeCmd(String target, String module, String args) {
        StringBuilder cmd = new StringBuilder();
        // 1.由于这个python接口文件调用了ansible的内部py模块，所以直接用jython调用可能有点麻烦
        // 故采用shell调用获取结果，之所以用这个，它的优点是输出结果格式比用纯shell执行ansible
        // 指令的输出格式要好些，这个接口输出的直接是json字符串。
        // 2.这里要注意python指令的路径和ansibleApi.py的路径,有时可能要加上完整路径
        cmd.append("python ansibleApi.py ");
        cmd.append(target).append(" ").append(module).append(" ");
        if (args.contains("'")) {
            cmd.append("\"").append(args).append("\"");
        }
        else {
            cmd.append("'").append(args).append("'");
        }
        Map<String, String> result = ShellUtil.execute(cmd.toString(), null);
        // System.out.println(result.get("output"));
        JSONObject ret = new JSONObject(result.get("output"));
        JSONObject results = new JSONObject();
        if (!ret.has(target)) {
            results.put("exitFlag", "FAILED");
            results.put("output", "No hosts matched, nothing to do,or other reasons.");
        }
        JSONObject targetObj = ret.getJSONObject(target);
        if (targetObj.has("rc") && targetObj.getInt("rc") == 0) {
            results.put("exitFlag", "SUCCESS");
            results.put("output", targetObj.getString("stdout"));
        }
        else if (targetObj.has("failed") && true == targetObj.getBoolean("failed")) {
            results.put("exitFlag", "FAILED");
            results.put("output", targetObj.getString("stderr"));
        }
        else if (targetObj.has("unreachable") && true == targetObj.getBoolean("unreachable")) {
            results.put("exitFlag", "FAILED");
            results.put("output", targetObj.getString("msg"));
        }
        System.out.println(results.toString());
        return results;
    }

    public String ansibleCmd(String target, String module, String args) {
        return ansibleCmd(null, target, module, args, null);
    }

    public String ansibleCmd(String target, String module, String args, Map<String, String> params) {
        return ansibleCmd(null, target, module, args, params);
    }

    public String ansibleCmd(String inventory_file, String target, String module, String args) {
        return ansibleCmd(inventory_file, target, module, args, null);
    }

    /**
     * 调用ansible模块，并执行ansible指令，返回执行结果
     */
    public String ansibleCmd(String inventory_file, String target, String module, String args,
        Map<String, String> params) {
        // 组装命令
        StringBuilder cmd = new StringBuilder();
        if (inventory_file == null || inventory_file.isEmpty()) {
            // 使用默认目录下的hosts文件
            cmd.append("ansible ").append(target).append(" -m ").append(module);
        }
        else {
            // 自定义目录下的hosts文件
            cmd.append("ansible ").append("-i ").append(inventory_file).append(" ").append(target).append(" -m ")
                .append(module);
        }
        if (args.contains("'")) {
            cmd.append(" -a \"").append(args).append("\"");
        }
        else {
            cmd.append(" -a '").append(args).append("'");
        }
        if (params != null) {
            cmd.append(" -e '");
            if (params != null) {
                for (Entry<String, String> entry : params.entrySet()) {
                    cmd.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            cmd.append("'");
        }
        Map<String, String> result = ShellUtil.execute(cmd.toString(), null);
        // 返回结果
        return result.get("output");
    }

    /**
     * 执行脚本 目前为简化全部使用默认用户名和密码,写死在Ansible配置文件中,后续在系统层面增加密钥管理
     */
    public JSONObject play(String target, String playbook) {
        return play(null, target, playbook, null, 300); // 默认timeout:5分钟,默认inventory：/etc/ansible/hosts
    }

    public JSONObject play(String target, String playbook, int timeout) {
        return play(null, target, playbook, null, timeout); // 默认inventory：/etc/ansible/hosts
    }

    public JSONObject play(String target, String playbook, Map<String, String> params) {
        return play(null, target, playbook, params, 300); // 默认timeout:5分钟,默认inventory：/etc/ansible/hosts
    }

    public JSONObject play(String inventory, String target, String playbook) {
        return play(inventory, target, playbook, null, 300); // 默认5分钟,指定inventory
    }

    public JSONObject play(String inventory, String target, String playbook, Map<String, String> params) {
        return play(inventory, target, playbook, params, 300); // 默认5分钟,指定inventory
    }

    public JSONObject play(String inventory, String target, String playbook, int timeout) {
        return play(inventory, target, playbook, null, timeout);
    }

    /**
     * target 执行任务的目标机器;返回值为OK，表示执行成功，ERROR表示执行失败
     */
    public JSONObject play(String inventory, String target, String playbook, Map<String, String> params, int timeout) {
        // PLAY RECAP
        // ********************************************************************
        // ],
        // 10.45.16.71 : ok=3 changed=2 unreachable=0 failed=0 ]]

        // 先判断目标是否可以正常连接，可能还处于初始化阶段(OS没有完全启动起来)
        boolean bFirst = true;
        long cur = System.currentTimeMillis();
        while (System.currentTimeMillis() - cur < timeout * 1000) {
            if (target == null) {
                break;
            }
            boolean rslt = testNode(inventory, target);
            if (rslt) {
                System.out.println("Node is OK.");
                break;
            }
            else {
                if (bFirst) {
                    System.out.println("Node is initializing,wait...");
                    bFirst = false;
                }
            }
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                System.out.println(e.toString());
            }
        }

        // 组装命令
        StringBuilder cmd = new StringBuilder();
        if (inventory != null) {
            inventory = "-i '" + inventory + "' ";
        }
        else {
            inventory = "";
        }
        cmd.append("ansible-playbook ").append(inventory).append(playbook);
        // 合入扩展参数
        cmd.append(" -e '").append("target=").append(target);
        if (params != null) {
            for (Entry<String, String> entry : params.entrySet()) {
                cmd.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        // cmd.append("'").append(" -vvvv");
        cmd.append("'");
        System.out.println("Run playbook: " + playbook);
        Map<String, String> result = ShellUtil.execute(cmd.toString(), null);
        // 分析结果
        String cmdResult = result.get("output");
        JSONObject retValue = new JSONObject();
        if (StringUtil.isNotEmpty(cmdResult) && cmdResult.indexOf("PLAY RECAP") != -1) {
            cmdResult = cmdResult.substring(cmdResult.indexOf("PLAY RECAP"));
            String[] lines = cmdResult.split(System.getProperty("line.separator"));
            for (String line : lines) {
                // 10.45.17.126 : ok=3 changed=2 unreachable=0 failed=0
                if (StringUtil.isEmpty(line)) {
                    continue;
                }
                if (line.contains("changed=") && line.contains("unreachable=") && line.contains("failed=")) {
                    String[] counts = line.split("unreachable=")[1].split("failed=");
                    if (counts.length == 2) {
                        if (Integer.parseInt(counts[0].trim()) > 0 || Integer.parseInt(counts[1].trim()) > 0) {
                            retValue.put("STATE", "FAILED");
                            retValue.put("ERROR_INFO", result.get("output"));
                            return retValue;
                        }
                    }
                }
            }
            retValue.put("STATE", "OK");
            return retValue;
        }
        retValue.put("STATE", "FAILED");
        retValue.put("ERROR_INFO", cmdResult);
        return retValue;
    }

    /**
     * 判断指定IP虚拟机是否启动完成（通过SSH服务测试）<br>
     * 通过ansible向目标机器执行一任意指令，根据返回值进行判断
     */
    public boolean testNode(String inventory, String host) {
        // 组装命令
        StringBuilder cmd = new StringBuilder();
        if (inventory != null) {
            inventory = "-i '" + inventory + "' ";
        }
        else {
            inventory = "";
        }
        cmd.append("ansible ").append(inventory).append(host).append(" -a 'echo hello' ");
        cmd.append("-e 'ansible_ssh_user=~nobody ansible_ssh_pass=~nobody'");
        Map<String, String> params = new HashMap<String, String>();
        // disable pipelining,否则无法从错误输出中区分出认证失败和虚拟机不可达
        params.put("ANSIBLE_SSH_PIPELINING", "False");
        Map<String, String> result = ShellUtil.execute(cmd.toString(), null);
        // 分析结果
        String cmdResult = result.get("output");
        // System.out.println(cmdResult);
        if (StringUtil.isNotEmpty(cmdResult)) {
            String[] lines = cmdResult.split(System.getProperty("line.separator"));
            for (String line : lines) {
                // uip_1 | FAILED => Authentication failure.
                // uip_2 | FAILED => Authentication failure.
                if (StringUtil.isEmpty(line) || !line.contains("FAILED =>")) {
                    continue;
                }
                if (!line.contains("Authentication failure")) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String args[]) {
        // java -classpath ansible.jar:json-20140107.jar:jython-standalone-2.5.3.jar ansible.AnsibleCaller
        AnsibleCaller.getInstance().executeCmd("10.45.80.26", "shell", "ps -a");
    }
}