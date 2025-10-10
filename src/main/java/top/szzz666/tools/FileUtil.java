package top.szzz666.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    public static void zipFolder(File rootFolder, File currentFolder, ZipOutputStream zos) throws IOException {
        File[] files = currentFolder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                zipFolder(rootFolder, file, zos);
            } else {
                // 计算相对路径
                String relativePath = rootFolder.toURI().relativize(file.toURI()).getPath();

                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(relativePath);
                    zos.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
        }
    }
    public static void deleteFolder(String folderPath) throws IOException {
        Path path = Paths.get(folderPath);

        // 使用 Files.walk 遍历文件夹，按反向顺序删除（先删除文件再删除目录）
        Files.walk(path)
                .sorted(Comparator.reverseOrder()) // 反向排序
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public static String copyFolder(String sourcePath, String targetPath) throws IOException {
        Path path = Paths.get(sourcePath);
        Path target = Paths.get(targetPath + "/" + path.getFileName().toString());

        // 如果目标文件夹不存在，则创建
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        // 使用 Files.walk 遍历源文件夹
        Files.walk(path)
                .forEach(from -> {
                    Path to = target.resolve(path.relativize(from));
                    try {
                        // 如果是目录则创建目录，否则复制文件
                        if (Files.isDirectory(from)) {
                            Files.createDirectories(to);
                        } else {
                            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        return target.toAbsolutePath().toString();
    }
    public static String readFileAsString(String filePath)  {
        String content = "";
        try {
            content =  Files.lines(Paths.get(filePath))
                    .collect(Collectors.joining(System.lineSeparator()));
        }catch (IOException ignored) {}
        return content;
    }
    public static void saveResourceFolder(String folderPath, String configPath, Class<?> clazz){
        if (!isFolder(configPath + folderPath)) {
            try {
                loadRecourseFromJarByFolder(folderPath, configPath, clazz);
            } catch (IOException e) {
                logger.error("资源文件加载失败{}", e.getMessage());
            }
        }
    }
    public static boolean isFolder(String path) {
        File folder = new File(path);
        return folder.exists();
    }
    public static void loadRecourseFromJarByFolder(String folderPath, String targetFolderPath, Class<?> clazz) throws IOException {
        URL url = clazz.getResource(folderPath);
        if (url == null) {
            throw new FileNotFoundException("文件夹 " + folderPath + " 在 JAR 中未找到。");
        }

        URLConnection urlConnection = url.openConnection();
        if (urlConnection instanceof JarURLConnection) {
            copyJarResources((JarURLConnection) urlConnection, folderPath, targetFolderPath, clazz);
        } else {
            copyFileResources(url, folderPath, targetFolderPath, clazz);
        }
    }

    private static void copyFileResources(URL url, String folderPath, String targetFolderPath, Class<?> clazz) throws IOException {
        File root = new File(url.getPath());
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        loadRecourseFromJarByFolder(folderPath + "/" + file.getName(), targetFolderPath, clazz);
                    } else {
                        loadRecourseFromJar(folderPath + "/" + file.getName(), targetFolderPath, clazz);
                    }
                }
            }
        }
    }

    private static void copyJarResources(JarURLConnection jarURLConnection, String folderPath, String targetFolderPath, Class<?> clazz) throws IOException {
        JarFile jarFile = jarURLConnection.getJarFile();
        Enumeration<JarEntry> entrys = jarFile.entries();
        while (entrys.hasMoreElements()) {
            JarEntry entry = entrys.nextElement();
            if (entry.getName().startsWith(jarURLConnection.getEntryName()) && !entry.getName().endsWith("/")) {
                loadRecourseFromJar("/" + entry.getName(), targetFolderPath, clazz);
            }
        }
        jarFile.close();
    }

    public static void loadRecourseFromJar(String path, String recourseFolder, Class<?> clazz) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("路径必须是绝对路径（以 '/' 开头）。");
        }

        if (path.endsWith("/")) {
            throw new IllegalArgumentException("路径不能以 '/' 结尾。");
        }

        int index = path.lastIndexOf('/');
        String filename = path.substring(index + 1);
        String folderPath = recourseFolder + path.substring(0, index + 1);

        File dir = new File(folderPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        filename = folderPath + filename;
        File file = new File(filename);

        if (!file.exists() && !file.createNewFile()) {
            System.err.println("创建文件：" + filename + " 失败");
            return;
        }

        byte[] buffer = new byte[1024];
        int readBytes;

        URL url = clazz.getResource(path);
        if (url == null) {
            throw new FileNotFoundException("文件 " + path + " 在 JAR 中未找到。");
        }

        URLConnection urlConnection = url.openConnection();
        InputStream is = urlConnection.getInputStream();
        OutputStream os = new FileOutputStream(file);
        try {
            while ((readBytes = is.read(buffer)) != -1) {
                os.write(buffer, 0, readBytes);
            }
        } finally {
            os.close();
            is.close();
        }
    }
}