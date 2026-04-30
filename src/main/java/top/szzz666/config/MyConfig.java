package top.szzz666.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static top.szzz666.Main.config;

public class MyConfig {

    @ConfigItem(key = "backupTasks", comment = "备份任务列表")
    public static List<Map<String, Object>> backupTasks;

    @ConfigItem(key = "cloudBackup", comment = "是否启用云备份")
    public static boolean cloudBackup = true;

    @ConfigItem(key = "baiduPan", comment = "是否启用百度网盘备份")
    public static boolean baiduPan = true;

    @ConfigItem(key = "pan123", comment = "是否启用123云盘备份")
    public static boolean pan123 = false;

    @ConfigItem(key = "baiduAppKey", comment = "百度网盘AppKey")
    public static String baiduAppKey = "你的AppKey";

    @ConfigItem(key = "baiduSecretKey", comment = "百度网盘SecretKey")
    public static String baiduSecretKey = "你的SecretKey";

    @ConfigItem(key = "baiduAppName", comment = "百度网盘应用名称")
    public static String baiduAppName = "你的百度应用名称";

    @ConfigItem(key = "pan123ClientId", comment = "123云盘ClientID")
    public static String pan123ClientId = "你的ClientID";

    @ConfigItem(key = "pan123ClientSecret", comment = "123云盘ClientSecret")
    public static String pan123ClientSecret = "你的ClientSecret";

    @ConfigItem(key = "backupFailNotify", comment = "备份失败时是否发送邮件通知")
    public static boolean backupFailNotify = false;

    @ConfigItem(key = "mailRecipient", comment = "接收通知的邮箱地址")
    public static String mailRecipient = "你的邮箱";

    @ConfigItem(key = "mailSubject", comment = "通知邮件标题")
    public static String mailSubject = "AutoBackup 备份失败！！！";

    @ConfigItem(key = "mailServerHost", comment = "SMTP邮件服务器地址")
    public static String mailServerHost = "smtp.qq.com";

    @ConfigItem(key = "mailServerPort", comment = "SMTP邮件服务器端口")
    public static int mailServerPort = 587;

    @ConfigItem(key = "senderUsername", comment = "发送者邮箱账号")
    public static String senderUsername = "发送者username";

    @ConfigItem(key = "senderPassword", comment = "发送者邮箱授权码")
    public static String senderPassword = "发送者password";

    @ConfigItem(key = "connectTimeout", comment = "连接超时时间（毫秒）")
    public static Integer connectTimeout = null;

    @ConfigItem(key = "writeTimeout", comment = "写入超时时间（毫秒）")
    public static Integer writeTimeout = null;

    @ConfigItem(key = "readTimeout", comment = "读取超时时间（毫秒）")
    public static Integer readTimeout = null;

    @ConfigItem(key = "baiduToken", comment = "百度网盘授权token")
    public static String baiduToken = null;

    @ConfigItem(key = "pan123Token", comment = "123云盘授权token")
    public static String pan123Token = null;

    public static void loadConfig() {
        // 初始化默认备份任务
        HashMap<String, Object> task = new HashMap<>();
        task.put("name", "test");
        task.put("path", "C:/Users/zhouy/File/kaiFa/测试备份/源文件夹");
        task.put("target", "C:/Users/zhouy/File/kaiFa/测试备份/目标文件夹");
        task.put("number", 3);
        task.put("corn", "0 0 4 * * ?");
        task.put("type", "local");
        ArrayList<Map<String, Object>> defaultTasks = new ArrayList<>();
        defaultTasks.add(task);
        backupTasks = defaultTasks;

        config = new EasyConfig("config.yml");
        config.loadFromClass(MyConfig.class);
        config.load();
    }
}
