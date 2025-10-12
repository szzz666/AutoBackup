package top.szzz666.config;

import java.util.ArrayList;
import java.util.HashMap;


import static top.szzz666.Main.config;


public class MyConfig {
    public static void loadConfig() {
        HashMap<String, Object> task = new HashMap<>();
        task.put("name", "测试备份");
        task.put("path", "C:/Users/zhouy/File/kaiFa/测试备份/源文件夹");
        task.put("target", "C:/Users/zhouy/File/kaiFa/测试备份/目标文件夹");
        task.put("number", 3);
        task.put("corn", "0 * * * * ?");
        task.put("cloud", "/server_backups");
        ArrayList<HashMap<String, Object>> tasks = new ArrayList<>();
        tasks.add(task);

//        HashMap<String, Object> token = new HashMap<>();
//        token.put("access_token", "access_token");
//        token.put("refresh_token", "refresh_token");
//        token.put("expires_in", 1);

        config = new EasyConfig("config.yml");
        config.add("备份任务", tasks);
        config.add("云备份", true);
        config.add("百度网盘", true);
        config.add("123云盘", false);
        config.add("百度AppKey", "你的AppKey");
        config.add("百度SecretKey", "你的SecretKey");
        config.add("百度应用名称", "你的百度应用名称");
        config.add("123云盘ClientID", "你的ClientID");
        config.add("123云盘ClientSecret", "你的ClientSecret");
        config.add("备份失败通知", false);
        config.add("邮件接收人", "你的邮箱");
        config.add("邮件标题", "AutoBackup 备份失败！！！");
        config.add("邮件服务器host", "smtp.qq.com");
        config.add("邮件服务器port", 587);
        config.add("发送者username", "发送者username");
        config.add("发送者password", "发送者password");
        config.add("baidu_token", null);
        config.add("123_token", null);
        config.load();
    }

}
