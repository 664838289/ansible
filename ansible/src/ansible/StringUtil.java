package ansible;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class StringUtil {

    private static String line_separator = null;

    private static DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");

    /**
     * 将列表转换成字符串
     * @param list 
     * @param separator f分隔符
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static String list2String(List list, String separator) {
        if (list == null || list.size() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(list.get(0).toString());
        for (int i = 1; i < list.size(); i++) {
            builder.append(separator).append(list.get(i).toString());
        }
        return builder.toString();
    }
    
    /**
     * 将列表转换成字符串,使用逗号作为分隔符
     * @param list
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static String list2String(List list) {
        
        return list2String(list,",");
    }
    /**
     * 连接字符串
     * @param separator，连接分隔符
     * @param strs
     * @return
     */
    public static String concat(String separator,String... strs){
        if(strs==null||strs.length==0){
            return null;
        }
        return list2String(Arrays.asList(strs),separator);
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        if (str == null || str.length() == 0)
            return true;
        return false;
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 将多个字符串拼接成一个字符串
     * 
     * @param strs
     * @return
     */
    public static String assemblyString(String... strs) {

        StringBuffer buffer = new StringBuffer();
        for (String str : strs) {
            buffer.append(str);
        }
        return buffer.toString();
    }

    /**
     * 将字符串List拼接成一个字符串
     * 
     * @param strList
     * @return
     */
    public static String assemblyString(List<String> strList) {

        StringBuffer buffer = new StringBuffer();
        for (String str : strList) {
            buffer.append(str);
        }
        return buffer.toString();
    }

    /**
     * Description: 获取当前系统换行符<br>
     * 
     * @param <br>
     * @return String <br>
     */
    public static String getLineSeperator() {
        if (line_separator == null) {
            line_separator = System.getProperty("line.separator", "\n");
        }
        return line_separator;
    }

    /**
     * Description: 计算两个字符串的相似度，使用Levenshtein Distance(编辑距离)算法实现
     * 
     * @param s
     * @param t
     * @return 返回百分比，数值越大标识相似度越高
     * @see http://blog.csdn.net/chinesesword/article/details/7640787
     */
    public static int strSimilarity(String s, String t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }

        int n = s.length();
        int m = t.length();

        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }

        int p[] = new int[n + 1]; // 'previous' cost array, horizontally
        int d[] = new int[n + 1]; // cost array, horizontally
        int _d[]; // placeholder to assist in swapping p and d

        // indexes into strings s and t
        int i; // iterates through s
        int j; // iterates through t

        char t_j; // jth character of t

        int cost; // cost

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left
                // and up +cost
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }

            // copy current distance counts to 'previous row' distance counts
            _d = p;
            p = d;
            d = _d;
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        int ld = p[n];
        // 计算相似度
        double sim = 1 - (double) ld / Math.max(s.length(), t.length());

        return new Double(sim * 100 + 0.5).intValue();
    }

    /**
     * 获取YYYYMMDDHH24MISS格式时间戳
     */
    public static String getYYYYMMDDHH24MISS() {
        return df.format(new Date());
    }

    /**
     * 获取YYYYMMDDHH24MISS格式时间戳
     */
    public static String getYYYYMMDDHH24MISS(Long timeMillis) {
        return df.format(new Date(timeMillis));
    }

    /**
     * 将多个字符串拼接程一个完整的文件路径
     */
    public static String buildPath(String seperatorChar, String... strs) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < strs.length; i++) {
            if (i != 0 && strs[i].length() > 0 && strs[i].charAt(0) == seperatorChar.charAt(0)) { // 首字符为路径符号的，要去掉
                buffer.append(strs[i].substring(1));
            } else {
                buffer.append(strs[i]);
            }

            if (i != strs.length - 1 && strs[i].length() > 0
                    && strs[i].charAt(strs[i].length() - 1) != seperatorChar.charAt(0)) { // 最后一个字符不是路径符的要增加
                buffer.append(seperatorChar);
            }
        }
        return buffer.toString();
    }

    /**
     * 将多个字符串拼接程一个完整的文件路径，使用本地路径格式
     */
    public static String buildLocalPath(String... strs) {
        String rslt = buildPath(File.separator, strs);
        // 转换成本地格式的目录
        rslt = rslt.replace("/", File.separator);
        rslt = rslt.replace("\\", File.separator);
        return rslt;
    }
    
    /**
     * 去除字符串数组中的空字符串
     * @param strArray
     * @return
     */
    public static ArrayList<String> removeSpaceStr(String[] strArray) {
        ArrayList<String> strList = new ArrayList<String>();
        for (String s : strArray) {
            if (!s.isEmpty()) {
                strList.add(s);
            }
        }
        return strList;
    }

}