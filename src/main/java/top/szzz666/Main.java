package top.szzz666;


import io.leego.banana.BananaUtils;
import io.leego.banana.Font;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.config.EasyConfig;
import top.szzz666.config.MyConfig;
import top.szzz666.tools.BaiduPanManager;
import top.szzz666.tools.MyJob;
import top.szzz666.tools.Pan123Manager;

import java.util.ArrayList;
import java.util.HashMap;



public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static EasyConfig config;
    public static Scheduler scheduler;

    public static void main(String[] args) {
        logger.info("AutoBackup 正在运行...");
        lineConsole(BananaUtils.bananaify("AutoBackup", Font.SMALL));
        logger.warn("如果遇到任何bug，请加入Q群进行反馈：894279534");
        MyConfig.loadConfig();
        try {
            scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();
            logger.info("定时备份调度器启动成功");
            ArrayList<HashMap<String, Object>> tasks = new ArrayList<>(config.get("备份任务"));
            for (HashMap<String, Object> task : tasks) {
                String path = (String) task.get("path");
                String target = (String) task.get("target");
                int number = (int) task.get("number");
                String corn = (String) task.get("corn");
                String cloud = (String) task.get("cloud");
                JobDetail job = JobBuilder.newJob(MyJob.class)
                        .withIdentity(path, "group1")
                        .usingJobData("path", path)
                        .usingJobData("target", target)
                        .usingJobData("cloud", cloud)
                        .usingJobData("number", number)
                        .build();
                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(path, "group1")
                        .withSchedule(CronScheduleBuilder.cronSchedule(corn))
                        .build();
                scheduler.scheduleJob(job, trigger);
                logger.info("定时备份：{} 启动成功", task.get("path"));
                if (config.get("云备份")) {
                    if (config.getBoolean("百度网盘"))
                        BaiduPanManager.getAccessToken();
                    if (config.getBoolean("123云盘"))
                        Pan123Manager.getAccessToken();
                }
            }
        } catch (Exception e) {
            logger.error("启动定时任务失败", e);
        }

    }

    public static void lineConsole(String s) {
        String[] lines = s.split("\n");
        for (String line : lines) {
            logger.info(line);
        }
    }

}