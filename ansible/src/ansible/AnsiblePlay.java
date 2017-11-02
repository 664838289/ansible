package ansible;

import org.json.JSONObject;

public class AnsiblePlay {
    // private final String defaultPath = "ansiblePlay/";

    public String callPlay(String play, int timeout) {
        // http://localhost:9999/ansible/playbook/call/play.yml/myserver?timeout=10
        // 使用默认路径下的play,以及target中不含/斜杠的主机组别名
        JSONObject result;
        if (timeout == 0) {
            result = AnsibleCaller.getInstance().play(null, play);
        }
        else {
            result = AnsibleCaller.getInstance().play(null, play, timeout);
        }
        // System.out.println(play + "  " + target + "  " + timeout);
        return result.toString();

    }

    public String callPlay(String play, String target, int timeout) {
        // http://localhost:9999/ansible/playbook/call/play.yml/myserver?timeout=10
        // 使用默认路径下的play,以及target中不含/斜杠的主机组别名
        JSONObject result;
        if (timeout == 0) {
            result = AnsibleCaller.getInstance().play(target, play);
        }
        else {
            result = AnsibleCaller.getInstance().play(target, play, timeout);
        }
        // System.out.println(play + "  " + target + "  " + timeout);
        return result.toString();

    }

    public String callPlay(String play, String target, String inventory, int timeout) {
        // http://localhost:9999/ansible/playbook/call/play.yml/myserver/hosyts?timeout=10
        // 使用默认路径下的play和hosyts,以及target中不含/斜杠的主机组别名
        JSONObject result;
        if (timeout == 0) {
            result = AnsibleCaller.getInstance().play(inventory, target, play);
        }
        else {
            result = AnsibleCaller.getInstance().play(inventory, target, play, timeout);
        }

        return result.toString();
    }
}
