package ansible;

import org.json.JSONObject;

public class AnsibleUtil {
    private static String ShellSucc = "| SUCCESS | rc=0 >>";

    private static String ShellFailed = "| FAILED | rc=";

    private static String ScriptSucc = "| SUCCESS =>";

    private static String ScriptFailed = "| FAILED! =>";

    // private static String success = "| SUCCESS ";
    // private static String failed = "| FAILED";

    private static String unreachable = "| UNREACHABLE! =>";

    /**
     * {"exitFlag":"SUCCESS","output":"字符串/json字符串"}
     */
    public static JSONObject shellResultToJson(String result) {
        JSONObject jResult = new JSONObject();
        JSONObject netNode = null;
        StringBuffer sb = null;
        if (StringUtil.isNotEmpty(result)) {
            String[] lines = result.split(System.getProperty("line.separator"));
            for (String line : lines) {
                if (StringUtil.isEmpty(line)) {
                    continue;
                }
                if (line.contains(ShellSucc) || line.contains(ShellFailed) || line.contains(unreachable)) {
                    netNode = new JSONObject();
                    sb = new StringBuffer();
                    if (line.contains("{")) {
                        sb.append("{\n");
                    }
                    netNode.put("exitFlag", line.split(" ")[2]);
                    netNode.put("output", sb);
                    jResult.put(line.split(" ")[0], netNode);
                    continue;
                }
                if (sb != null) {
                    sb.append(line + "\n");
                }
            }
        }
        return jResult;
    }

    /**
     * {"exitFlag":"SUCCESS","output":"json字符串"}
     */
    public static JSONObject scriptResultToJson(String result) {
        JSONObject jResult = new JSONObject();
        JSONObject netNode = null;
        StringBuffer sb = null;
        if (StringUtil.isNotEmpty(result)) {
            String[] lines = result.split(System.getProperty("line.separator"));
            for (String line : lines) {
                if (StringUtil.isEmpty(line)) {
                    continue;
                }
                if (line.contains(ScriptSucc) || line.contains(ScriptFailed) || line.contains(unreachable)) {
                    netNode = new JSONObject();
                    sb = new StringBuffer();
                    sb.append("{");
                    netNode.put("exitFlag", line.split(" ")[2]);
                    netNode.put("output", sb);
                    jResult.put(line.split(" ")[0], netNode);
                    continue;
                }
                if (sb != null) {
                    sb.append(line);
                }
            }
        }
        return jResult;
    }
}
