package ansible;

import java.util.Map;

/**
 * Created by user on 2017/5/18.
 */
public class AnsibleCmdDto {
    private String module;

    private String target;

    private String inventory;

    private String args;

    private Map<String, String> params;

    public void setModule(String module) {
        this.module = module;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public String getModule() {
        return module;
    }

    public String getTarget() {
        return target;
    }

    public String getInventory() {
        return inventory;
    }

    public String getArgs() {
        return args;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
