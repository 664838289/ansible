package test;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JmxTest {
    /**
     * 建立连接
     *
     * @param ip
     * @param jmxport
     * @return
     */
    public static MBeanServerConnection createMBeanServer(String ip, String jmxport) {
        try {
            String jmxURL = "service:jmx:rmi:///jndi/rmi://" + ip + ":" + jmxport + "/jmxrmi";
            // jmx
            // url
            JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);
            JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;

        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println(ip + "的中间件不可以达");
        }
        return null;
    }

    public static void main(String[] args) throws MalformedObjectNameException, IOException, IntrospectionException,
        InstanceNotFoundException, ReflectionException, IntrospectionException {
        // java -classpath ansible.jar test.JmxTest
        // 连接到某一个jmx服务端
        MBeanServerConnection mbsc = createMBeanServer("10.45.80.26", "8569");
        // 查询某一type Object的所有MBean attribute
        ObjectName threadObjName = new ObjectName("java.lang:type=Threading");
        MBeanInfo mbInfo = mbsc.getMBeanInfo(threadObjName);
        MBeanAttributeInfo[] attrInfo = mbInfo.getAttributes();

        System.out.println("Attributes for object: Threading :\n");
        for (MBeanAttributeInfo attr : attrInfo) {
            System.out.println("Name: " + attr.getName() + "\tType: " + attr.getType() + "\n");
        }

        ObjectName threadObjName2 = new ObjectName("java.lang:type=Memory");
        MBeanInfo mbInfo2 = mbsc.getMBeanInfo(threadObjName2);
        MBeanAttributeInfo[] attrInfo2 = mbInfo2.getAttributes();

        System.out.println("Attributes for object: Memory :\n");
        for (MBeanAttributeInfo attr : attrInfo2) {
            System.out.println("Name: " + attr.getName() + "\tType: " + attr.getType() + "\n");
        }

    }

}
