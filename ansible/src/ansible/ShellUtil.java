package ansible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellUtil {

    /**
     * 直接执行命令并返回结果
     * 
     * @return map {"exitFlag": 0表示正常结束，其他表示异常 ,"output": 窗口输出结果}
     */
    public static Map<String, String> execute(String cmd, Map<String, String> envParams) {
        List<String> cmdList = new ArrayList<String>();
        // 加上shell
        cmdList.add("sh");
        cmdList.add("-c");
        cmdList.add(cmd);
        StringBuffer rsltStrBuffer = new StringBuffer(); // 保存返回的结果信息
        int exitFlag = -1; // 等待SHELL线程完成的标识
        Process proc = null;
        try {
            // System.out.println("Start Command :"+cmdList.toString());
            // 启动命令
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            if (envParams != null) {
                pb.environment().putAll(envParams);
            }

            pb.redirectErrorStream(true);
            proc = pb.start();

            // 读取命令返回信息和错误信息
            readNormalAndErrorInfo(rsltStrBuffer, proc);

            // 等待命令执行完成，并返回命令执行状态
            exitFlag = proc.waitFor();
        }
        catch (Exception e) {
            System.out.println("Command [" + cmdList + "] Execute  Error: " + e.toString());
        }
        finally {
            if (proc != null)
                proc.destroy();
        }

        Map<String, String> result = new HashMap<String, String>();
        result.put("exitFlag", String.valueOf(exitFlag));
        result.put("output", rsltStrBuffer.toString());
        return result;
    }

    private static void readNormalAndErrorInfo(StringBuffer rsltStrBuffer, Process proc) throws IOException {
        try {
            int c;
            StringBuilder line = new StringBuilder();
            while ((c = proc.getInputStream().read()) != -1) {
                rsltStrBuffer.append((char) c);
                if (c == 10 || c == 13) {
                    String logStr = line.toString();
                    // 去掉空行
                    if (logStr.length() > 0) {
                        System.out.println(logStr);
                        line.delete(0, line.length());
                    }
                }
                else {
                    line.append((char) c);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}