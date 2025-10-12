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
import java.util.Scanner;


public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static EasyConfig config;
    public static Scheduler scheduler;

    public static void main(String[] args) throws Exception {
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
                String name = (String) task.get("name");
                String path = (String) task.get("path");
                String target = (String) task.get("target");
                int number = (int) task.get("number");
                String corn = (String) task.get("corn");
                String cloud = (String) task.get("cloud");
                JobDetail job = JobBuilder.newJob(MyJob.class)
                        .withIdentity(name, "group1")
                        .usingJobData("name", name)
                        .usingJobData("path", path)
                        .usingJobData("target", target)
                        .usingJobData("cloud", cloud)
                        .usingJobData("number", number)
                        .build();
                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(name, "group1")
                        .withSchedule(CronScheduleBuilder.cronSchedule(corn))
                        .build();
                scheduler.scheduleJob(job, trigger);
                logger.info("定时备份：{} 启动成功", name);

            }
        } catch (Exception e) {
            logger.error("启动定时任务失败", e);
        }

        if (config.get("云备份")) {
            if (config.getBoolean("百度网盘"))
                BaiduPanManager.getAccessToken();
            if (config.getBoolean("123云盘"))
                Pan123Manager.getAccessToken();
        }
        // 命令
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine().trim();
            String[] split = input.split(" ");
            String command = split[0];
            if ("help".equalsIgnoreCase(command)) {
                logger.info("help: 显示帮助信息");
                logger.info("stop: 停止定时备份");
                logger.info("backup [任务名称]: 立即执行指定任务");
            }
            if ("stop".equalsIgnoreCase(command)) {
                scheduler.shutdown();
                System.exit(0);
            }
            if ("backup".equalsIgnoreCase(command)){
                JobKey jobKey = new JobKey(split[1], "group1");
                scheduler.triggerJob(jobKey);
            }
        }
    }

    public static void lineConsole(String s) {
        String[] lines = s.split("\n");
        for (String line : lines) {
            logger.info(line);
        }
    }

}