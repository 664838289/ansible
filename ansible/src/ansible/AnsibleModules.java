package ansible;

/**
 * Ansible的内部模块
 * 
 * @author user
 */
public class AnsibleModules {
    // 文件组装模块，主要是将多份配置文件组装为一份配置文件
    public static final String ASSEMBLE = "assemble ";

    // 计划任务管理
    public static final String CRON = "cron";

    // 复制文件到远程主机
    public static final String COPY = "copy";

    // 在远程主机上执行命令
    public static final String COMMAND = "command";

    // 文件属性设置
    public static final String FILE = "file";

    // 在块设备上创建文件系统
    public static final String FILESYSYTEM = "filesystem";

    // 文件拉取模块，主要是将远程主机中的文件拷贝到本机中
    public static final String FETCH = "fetch";

    // 系统用户组管理
    public static final String GROUP = "group";

    // 下载某url的资源
    public static final String GET_URL = "get_url";

    // 主要用来修改主机的名称
    public static final String HOSTNAME = "hostname ";

    // ini文件管理模块主要是用来设置ini文件的格式的文件
    public static final String INI_FILE = "ini_file ";

    // 配置挂载点
    public static final String MOUNT = "mount";

    // 主要是无意义的测试模块
    public static final String PING = "ping";

    // ssh命令执行模块
    public static final String RAW = "raw";

    // 将本地脚本复制到远程主机并运行
    public static final String SCRIPT = "script";

    // 系统服务管理
    public static final String SERVICE = "service";

    // 收集远程主机的facts
    public static final String SETUP = "setup";

    // 使用rsync同步文件
    public static final String SYNCHRONIZE = "synchronize";

    // 切换到某个shell执行指定的指令，与command不同的是，此模块可以支持命令管道
    public static final String SHELL = "shell";

    // 进行文档内变量的替换的模块
    public static final String TEMPLATE = "template ";

    // 系统用户账号管理
    public static final String USER = "user";

    // 软件包安装管理
    public static final String YUM = "yum";

}
