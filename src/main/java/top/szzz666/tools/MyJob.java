package top.szzz666.tools;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import static top.szzz666.Main.config;
import static top.szzz666.tools.BaiduPanManager.*;
import static top.szzz666.tools.FileUtil.*;


public class MyJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(MyJob.class);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        String name = jobExecutionContext.getJobDetail().getJobDataMap().getString("name");
        String path = jobExecutionContext.getJobDetail().getJobDataMap().getString("path");
        String target = jobExecutionContext.getJobDetail().getJobDataMap().getString("target");
        String cloud = jobExecutionContext.getJobDetail().getJobDataMap().getString("cloud");
        int number = jobExecutionContext.getJobDetail().getJobDataMap().getInt("number");
        logger.info("开执行备份任务: {}", name);
        try {
            String zipFileName = backupFolderToZip(path, target);
            logger.info("备份成功完成: {} -> {}", path, target);
            cleanOldBackups(target, number);
            logger.info("清理完成，保留最新的{}个备份文件", number);
            if (config.getBoolean("云备份")) {
                String localPath = target + "/" + zipFileName;
                String remotePath = cloud + "/" + zipFileName;
                if (config.getBoolean("百度网盘"))
                    BaiduPanManager.uploadLargeFile(getAccessToken(), localPath,
                            "/apps/" + config.getString("百度应用名称") + remotePath);
                if (config.getBoolean("123云盘"))
                    Pan123Manager.uploadLargeFile(localPath, remotePath);
                logger.info("云备份成功完成: {} -> {}", localPath, remotePath);
                if (config.getBoolean("百度网盘"))
                    BaiduPanManager.manageBackups(getAccessToken(), "/apps/" + config.getString("百度应用名称") + cloud, number);
                if (config.getBoolean("123云盘"))
                    Pan123Manager.manageBackups(number);
                logger.info("云备份清理完成，保留最新的{}个备份文件", number);
            }
        } catch (Exception e) {
            logger.error("备份或清理过程中出错", e);
            if (config.getBoolean("备份失败通知"))
                sendEmail(config.get("邮件服务器host"), config.get("邮件服务器port"), config.get("发送者username"),
                        config.get("发送者password"), config.get("邮件接收人"), config.get("邮件标题"),
                        e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    public static String backupFolderToZip(String sourceFolderPath, String targetFolderPath) throws IOException {
        File sourceFolder = new File(sourceFolderPath);
        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            throw new IOException("源路径不是一个有效的文件夹: " + sourceFolderPath);
        }
        File targetFolder = new File(targetFolderPath);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }
        // 使用源文件夹名作为ZIP文件名
        String zipFileName = sourceFolder.getName() + "_AutoBackup_" + getTimeStr() + ".zip";
        File zipFile = new File(targetFolder, zipFileName);
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFolder(sourceFolder, sourceFolder, zos);
        } catch (IOException e) {
            deleteFolder(zipFile.getAbsolutePath());
            logger.info("文件夹 {} 已被其他程序占用，复制到临时文件夹再进行备份。", sourceFolderPath);
            String copyPath = copyFolder(sourceFolderPath, targetFolderPath);
            zipFileName = backupFolderToZip(copyPath, targetFolderPath);
            deleteFolder(copyPath);
            logger.info("临时文件夹 {} 已删除。", copyPath);
        }
        return zipFileName;
    }


    private static void cleanOldBackups(String backupFolderPath, int keepCount) throws IOException {
        Path folder = Paths.get(backupFolderPath);
        // 获取文件夹中所有ZIP备份文件并按创建时间排序（从新到旧）
        List<File> backupFiles = Arrays.stream(folder.toFile().listFiles()).filter(file -> file.isFile() && file.getName().endsWith(".zip")).sorted((f1, f2) -> {
            try {
                BasicFileAttributes attr1 = Files.readAttributes(f1.toPath(), BasicFileAttributes.class);
                BasicFileAttributes attr2 = Files.readAttributes(f2.toPath(), BasicFileAttributes.class);
                return attr2.creationTime().compareTo(attr1.creationTime());
            } catch (IOException e) {
                return 0;
            }
        }).collect(Collectors.toList());

        if (backupFiles.size() <= keepCount) {
            logger.debug("备份文件数量不足{}个，无需清理", keepCount);
            return;
        }

        // 保留最新的keepCount个文件，删除其余文件
        for (int i = keepCount; i < backupFiles.size(); i++) {
            File fileToDelete = backupFiles.get(i);
            logger.debug("正在删除老旧备份文件: {}", fileToDelete.getName());
            if (!fileToDelete.delete()) {
                logger.warn("无法删除文件: {}", fileToDelete.getName());
            }
        }
    }


    private static String getTimeStr() {
        long currentTimeMillis = System.currentTimeMillis();
        Date date = new Date(currentTimeMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(date);
    }

    public static void sendEmail(String host, int port, final String userName, final String password, String toAddress,
                                 String subject, String message) {
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", port);
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            Authenticator auth = new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(userName, password);
                }
            };
            Session session = Session.getInstance(properties, auth);
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(userName));
            InternetAddress[] toAddresses = {new InternetAddress(toAddress)};
            msg.setRecipients(Message.RecipientType.TO, toAddresses);
            msg.setSubject(subject);
            msg.setSentDate(new Date());
            msg.setText(message);
            // 发送消息
            Transport.send(msg);
            logger.info("备份失败通知邮件发送成功");
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
