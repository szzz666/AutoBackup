package top.szzz666.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static top.szzz666.Main.config;

public class MyConfig {

    // ==================== 备份配置 ====================
    @ConfigItem(key = "backup.tasks", comment = "备份任务列表")
    public static List<Map<String, Object>> backupTasks;

    // ==================== 云备份配置 ====================
    @ConfigItem(key = "cloud.enabled", comment = "是否启用云备份")
    public static boolean cloudEnabled = true;

    @ConfigItem(key = "cloud.baidu.enabled", comment = "是否启用百度网盘备份")
    public static boolean baiduEnabled = true;

    @ConfigItem(key = "cloud.baidu.appKey", comment = "百度网盘AppKey")
    public static String baiduAppKey = "你的AppKey";

    @ConfigItem(key = "cloud.baidu.secretKey", comment = "百度网盘SecretKey")
    public static String baiduSecretKey = "你的SecretKey";

    @ConfigItem(key = "cloud.baidu.appName", comment = "百度网盘应用名称")
    public static String baiduAppName = "你的百度应用名称";

    @ConfigItem(key = "cloud.baidu.token", comment = "百度网盘授权token")
    public static String baiduToken = null;

    @ConfigItem(key = "cloud.pan123.enabled", comment = "是否启用123云盘备份")
    public static boolean pan123Enabled = false;

    @ConfigItem(key = "cloud.pan123.clientId", comment = "123云盘ClientID")
    public static String pan123ClientId = "你的ClientID";

    @ConfigItem(key = "cloud.pan123.clientSecret", comment = "123云盘ClientSecret")
    public static String pan123ClientSecret = "你的ClientSecret";

    @ConfigItem(key = "cloud.pan123.token", comment = "123云盘授权token")
    public static String pan123Token = null;

    // ==================== 通知配置 ====================
    @ConfigItem(key = "notify.enabled", comment = "备份失败时是否发送邮件通知")
    public static boolean notifyEnabled = false;

    @ConfigItem(key = "notify.mail.recipient", comment = "接收通知的邮箱地址")
    public static String mailRecipient = "你的邮箱";

    @ConfigItem(key = "notify.mail.subject", comment = "通知邮件标题")
    public static String mailSubject = "AutoBackup 备份失败！！！";

    @ConfigItem(key = "notify.mail.host", comment = "SMTP邮件服务器地址")
    public static String mailHost = "smtp.qq.com";

    @ConfigItem(key = "notify.mail.port", comment = "SMTP邮件服务器端口")
    public static int mailPort = 587;

    @ConfigItem(key = "notify.mail.senderUsername", comment = "发送者邮箱账号")
    public static String mailSenderUsername = "发送者username";

    @ConfigItem(key = "notify.mail.senderPassword", comment = "发送者邮箱授权码")
    public static String mailSenderPassword = "发送者password";

    // ==================== 超时配置 ====================
    @ConfigItem(key = "timeout.connect", comment = "连接超时时间（秒）")
    public static Integer connectTimeout = 30;

    @ConfigItem(key = "timeout.write", comment = "写入超时时间（秒）")
    public static Integer writeTimeout = 60;

    @ConfigItem(key = "timeout.read", comment = "读取超时时间（秒）")
    public static Integer readTimeout = 30;

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
