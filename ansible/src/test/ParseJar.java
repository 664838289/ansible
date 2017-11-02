package test;

import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ParseJar {

    public static void getJarName(String jarFile) throws Exception {
        try {
            // 通过jarFile和JarEntry得到所有的类
            JarFile jar = new JarFile(jarFile);
            // 返回zip文件条目的枚举
            Enumeration<JarEntry> enumFiles = jar.entries();
            JarEntry entry;

            // 测试此枚举是否包含更多的元素
            while (enumFiles.hasMoreElements()) {
                entry = (JarEntry) enumFiles.nextElement();
                if (entry.getName().indexOf("META-INF") < 0) {
                    String classFullName = entry.getName();
                    if (classFullName.endsWith(".class")) {
                        // 去掉后缀.class
                        String className = classFullName.substring(0, classFullName.length() - 6).replace("/", ".");
                        // 打印类名
                        System.out.println(className);
                    }
                    //以'/'结尾的是一个包
                }
            }
            jar.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
         //getJarName("E:\\WorkSpace\\javax.ws.rs-api-2.0.1.jar");
        //getJarName("E:\\WorkSpace\\lib\\core.jar");
        // getJarName("E:\\zcm\\zcm-resource\\target\\zcm-resource-0.0.1-SNAPSHOT.jar");
    }

}
