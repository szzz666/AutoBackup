package top.szzz666.tools;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
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
        String type = jobExecutionContext.getJobDetail().getJobDataMap().getString("type");
        int number = jobExecutionContext.getJobDetail().getJobDataMap().getInt("number");
        logger.info("开执行备份任务: {}", name);
        try {
            switch (type) {
                case "local": {
                    backupFolderToZip(path, target);
                    logger.info("备份成功完成: {} -> {}", path, target);
                    cleanOldBackups(target, number);
                    logger.info("清理完成，保留最新的{}个备份文件", number);
                    break;
                }
                case "baidu_pan": {
                    if (config.getBoolean("cloud.enabled")) {
                        if (config.getBoolean("cloud.baidu.enabled")) {
                            String temp_path = "/baidu_temp";
                            String zipFileName = backupFolderToZip(path, temp_path);
                            String temp_file_path = temp_path + "/" + zipFileName;
//                            String cloud_file_path = target + "/" + zipFileName;
                            BaiduPanManager.uploadLargeFile(getAccessToken(), temp_file_path, "/apps/" + config.getString("cloud.baidu.appName") + target + "/" + zipFileName);
                            BaiduPanManager.manageBackups(getAccessToken(), "/apps/" + config.getString("cloud.baidu.appName") + target, number);
                            Path tempFile = Path.of(temp_file_path);
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException ex) {
                                logger.warn("删除临时文件失败: {}", temp_file_path, ex);
                            }
                            break;
                        }
                    }

                }
                case "123pan": {
                    if (config.getBoolean("cloud.enabled")) {
                        if (config.getBoolean("cloud.pan123.enabled")) {
                            String temp_path = "/123temp";
                            String zipFileName = backupFolderToZip(path, temp_path);
                            String temp_file_path = temp_path + "/" + zipFileName;
                            String cloud_file_path = target + "/" + zipFileName;
                            Pan123Manager.uploadLargeFile(temp_file_path, cloud_file_path);
                            Pan123Manager.manageBackups(number);
                            Path tempFile2 = Path.of(temp_file_path);
                            try {
                                Files.deleteIfExists(tempFile2);
                            } catch (IOException ex) {
                                logger.warn("删除临时文件失败: {}", temp_file_path, ex);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("备份或清理过程中出错", e);
            if (config.getBoolean("notify.enabled"))
                sendEmail(config.getString("notify.mail.host"), config.getInt("notify.mail.port"), config.getString("notify.mail.senderUsername"), config.getString("notify.mail.senderPassword"), config.getString("notify.mail.recipient"), config.getString("notify.mail.subject"), e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
        }

    }

    public static String backupFolderToZip(String sourceFolderPath, String targetFolderPath) throws IOException {
        Path sourcePath = Path.of(sourceFolderPath);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IOException("源路径不是一个有效的文件夹: " + sourceFolderPath);
        }
        Path targetPath = Path.of(targetFolderPath);
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        // 使用源文件夹名作为ZIP文件名
        String zipFileName = sourcePath.getFileName() + "_AutoBackup_" + getTimeStr() + ".zip";
        Path zipFilePath = targetPath.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            zipFolder(sourcePath.toFile(), sourcePath.toFile(), zos);
        } catch (IOException e) {
            deleteFolder(zipFilePath.toAbsolutePath().toString());
            logger.info("文件夹 {} 已被其他程序占用，复制到临时文件夹再进行备份。", sourceFolderPath);
            String copyPath = copyFolder(sourceFolderPath, targetFolderPath);
            zipFileName = backupFolderToZip(copyPath, targetFolderPath);
            deleteFolder(copyPath);
            logger.info("临时文件夹 {} 已删除。", copyPath);
        }
        return zipFileName;
    }


    private static void cleanOldBackups(String backupFolderPath, int keepCount) throws IOException {
        Path folder = Path.of(backupFolderPath);
        if (!Files.isDirectory(folder)) {
            logger.warn("备份文件夹不存在或不是目录: {}", backupFolderPath);
            return;
        }
        // 使用 Files.list 获取文件夹中所有ZIP备份文件并按创建时间排序（从新到旧）
        List<Path> backupFiles;
        try (var stream = Files.list(folder)) {
            backupFiles = stream
                    .filter(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".zip"))
                    .sorted((f1, f2) -> {
                        try {
                            BasicFileAttributes attr1 = Files.readAttributes(f1, BasicFileAttributes.class);
                            BasicFileAttributes attr2 = Files.readAttributes(f2, BasicFileAttributes.class);
                            return attr2.creationTime().compareTo(attr1.creationTime());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
        }

        if (backupFiles.size() <= keepCount) {
            logger.debug("备份文件数量不足{}个，无需清理", keepCount);
            return;
        }

        // 保留最新的keepCount个文件，删除其余文件
        for (int i = keepCount; i < backupFiles.size(); i++) {
            Path fileToDelete = backupFiles.get(i);
            logger.debug("正在删除老旧备份文件: {}", fileToDelete.getFileName());
            try {
                Files.deleteIfExists(fileToDelete);
            } catch (IOException e) {
                logger.warn("无法删除文件: {}", fileToDelete.getFileName(), e);
            }
        }
    }


    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static String getTimeStr() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    public static void sendEmail(String host, int port, final String userName, final String password, String toAddress, String subject, String message) {
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
            logger.error("发送通知邮件失败", e);
        }
    }
}
