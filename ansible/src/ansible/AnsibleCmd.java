package ansible;

public class AnsibleCmd {

    public String execCmd(String target, String module, String args) {
        String result = null;
        result = AnsibleCaller.getInstance().ansibleCmd(target, module, args);
        // System.out.println(target + "  " + module + "  " + args);
        return result;
    }

    public String execCmd(String inventory, String target, String module, String args) {
        String result = null;
        result = AnsibleCaller.getInstance().ansibleCmd(inventory, target, module, args);
        // System.out.println(target + "  " + module + "  " + args);
        return result;
    }
}
