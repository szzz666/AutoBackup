package top.szzz666.tools;

import com.google.gson.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static top.szzz666.Main.config;


public class BaiduPanManager {
    private static final Logger logger = LoggerFactory.getLogger(BaiduPanManager.class);
    private static final String APP_KEY = config.getString("baiduAppKey");
    private static final String SECRET_KEY = config.getString("baiduSecretKey");

    // 全局常量配置
    private static final String REDIRECT_URI = "oob";
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;


    public static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getInt("connectTimeout"), TimeUnit.SECONDS)
            .writeTimeout(config.getInt("writeTimeout"), TimeUnit.SECONDS)
            .readTimeout(config.getInt("readTimeout"), TimeUnit.SECONDS)
            .build();
    public static final Gson gson = new Gson();


    public static String getAccessToken() throws Exception {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> tokenData = (HashMap<String, Object>) config.get("baiduToken");
        if (tokenData != null) {
            long expiresAt = Long.parseLong(tokenData.get("expires_in").toString());
            if (System.currentTimeMillis() / 1000 < expiresAt) {
                logger.info("[百度网盘] 使用本地 access_token");
                return (String) tokenData.get("access_token");
            } else {
                logger.info("[百度网盘] access_token 已过期，尝试刷新...");
                String refreshToken = (String) tokenData.get("refresh_token");
                if (refreshToken != null) {
                    String refreshTokenUrl = String.format(
                            "https://openapi.baidu.com/oauth/2.0/token?" +
                                    "grant_type=refresh_token&" +
                                    "refresh_token=%s&" +
                                    "client_id=%s&" +
                                    "client_secret=%s",
                            refreshToken, APP_KEY, SECRET_KEY);
                    JsonObject refreshTokenData = makeApiRequest(refreshTokenUrl, null);
                    if (refreshTokenData.has("access_token")) {
                        SaveToken(refreshTokenData);
                        return refreshTokenData.get("access_token").getAsString();
                    } else {
                        for (int i = 0; i < 3; i++) {
                            JsonObject refreshTokenData1 = makeApiRequest(refreshTokenUrl, null);
                            if (refreshTokenData1.has("access_token")) {
                                SaveToken(refreshTokenData1);
                                return refreshTokenData1.get("access_token").getAsString();
                            }
                            logger.error("[百度网盘] 刷新 access_token 失败，正在重试第 {} 次...", i + 1);
                            Thread.sleep(3000);
                        }
                    }
                }
            }
        }
        return authorizeNewToken();
    }

    private static void SaveToken(JsonObject data) {
        String accessToken = data.get("access_token").getAsString();
        long expires_in = data.get("expires_in").getAsInt();
        String refresh_token = data.get("refresh_token").getAsString();
        HashMap<String, Object> token = new HashMap<>();
        token.put("access_token", accessToken);
        token.put("refresh_token", refresh_token);
        token.put("expires_in", System.currentTimeMillis() / 1000 + expires_in - 300);
        config.set("baiduToken", token);
        logger.info("[百度网盘] 保存 access_token 成功。");
    }


    private static String authorizeNewToken() throws Exception {
        // 生成授权URL
        String authUrl = String.format(
                "https://openapi.baidu.com/oauth/2.0/authorize?" +
                        "response_type=code&" +
                        "client_id=%s&" +
                        "redirect_uri=%s&" +
                        "scope=basic,netdisk",
                APP_KEY, REDIRECT_URI);
        logger.info("[百度网盘] 首次运行或授权失败，请在浏览器打开以下链接，登录并授权：\n{}", authUrl);
        logger.info("[百度网盘] 请输入浏览器返回的 code: ");
        Scanner scanner = new Scanner(System.in);
        String code = scanner.nextLine().trim();
        // 使用code交换Token
        String ExchangeUrl = String.format(
                "https://openapi.baidu.com/oauth/2.0/token?" +
                        "grant_type=authorization_code&" +
                        "code=%s&" +
                        "client_id=%s&" +
                        "client_secret=%s&" +
                        "redirect_uri=%s",
                code, APP_KEY, SECRET_KEY, REDIRECT_URI);
        JsonObject data = makeApiRequest(ExchangeUrl, null);
        if (data.has("access_token")) {
            logger.info("[百度网盘] 授权成功，正在保存 access_token...");
            SaveToken(data);
            return data.get("access_token").getAsString();
        }
        logger.error("[百度网盘] 获取 access_token 失败: {}", data);
        return getAccessToken();
    }

    private static JsonObject makeApiRequest(String url, String formData) throws IOException {
        Request request;
        if (formData == null) {
            request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "pan.baidu.com")
                    .build();
        } else {
            request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(formData, MediaType.parse("application/x-www-form-urlencoded")))
                    .header("User-Agent", "pan.baidu.com")
                    .build();
        }
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("意外代码 {}", response);
            }
            String responseBody = null;
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            logger.debug(responseBody);
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("errno") != null && jsonObject.get("errno").getAsInt() > 0) {
                logger.error("请求失败: {}", responseBody);
            }
            return jsonObject;
        }
    }


    public static void uploadLargeFile(String accessToken, String localPath, String remotePath) throws Exception {
        Path path = Path.of(localPath);
        if (!Files.exists(path)) {
            logger.info("[百度网盘] 开始上传文件: {}", path.getFileName());
            return;
        }
        // 获取文件大小
        long fileSize = Files.size(path);
        logger.info("[百度网盘] 文件大小: {} MB", fileSize / 1024.0 / 1024.0);

        // ===== 文件校验阶段 =====
        logger.info("[百度网盘] 正在执行文件完整性预上传自检...");
        List<String> blockMd5ListPass1 = getBlockMd5List(localPath, 1);
        List<String> blockMd5ListPass2 = getBlockMd5List(localPath, 2);
        if (!blockMd5ListPass1.equals(blockMd5ListPass2)) {
            throw new RuntimeException("本地文件读取不一致，可能存在硬件问题。");
        }
        logger.info("[百度网盘] 文件完整性自检通过。文件在本地是稳定可读的。");
        int totalParts = blockMd5ListPass1.size();
        logger.info("[百度网盘] 文件校验信息计算完成，共 {} 个分块。", totalParts);
        // ===== 预上传阶段 =====
        logger.info("[百度网盘] 正在进行预上传...");
        String precreateUrl = "https://pan.baidu.com/rest/2.0/xpan/file?method=precreate&access_token=" + accessToken;
        String formData = "path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8) +
                "&size=" + fileSize +
                "&isdir=0" +
                "&autoinit=1" +
                "&block_list=" + URLEncoder.encode(gson.toJson(blockMd5ListPass1), StandardCharsets.UTF_8);
        JsonObject precreateResp = makeApiRequest(precreateUrl, formData);
        String uploadId = precreateResp.get("uploadid").getAsString();
        if (uploadId == null) throw new RuntimeException("预上传失败: " + precreateResp);
        logger.info("[百度网盘] 预上传成功");

        // ===== 分片上传阶段 =====
        logger.info("[百度网盘] 开始分片上传...");
        for (int idx = 0; idx < totalParts; idx++) {
            // 实际分片上传（已注释掉进度打印）
            uploadPart(accessToken, remotePath, uploadId, idx, localPath);
        }
        logger.info("[百度网盘] 所有分片上传完毕。");

        // ===== 合并文件阶段 =====
        logger.info("[百度网盘] 正在合并文件...");
        String createUrl = "https://pan.baidu.com/rest/2.0/xpan/file?method=create&access_token=" + accessToken;
        String createFormData = "path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8) +
                "&size=" + fileSize +
                "&isdir=0" +
                "&uploadid=" + uploadId +
                "&block_list=" + URLEncoder.encode(gson.toJson(blockMd5ListPass1), StandardCharsets.UTF_8);
        JsonObject createResp = makeApiRequest(createUrl, createFormData);
        if (!createResp.has("fs_id")) throw new RuntimeException("合并文件失败: " + createResp);
        logger.info("[百度网盘] 文件上传成功! 网盘路径: {}", createResp.get("path").getAsString());
    }


    private static void uploadPart(String accessToken, String remotePath, String uploadId,
                                   int partIndex, String localPath) throws Exception {
        int retryCount = 0;
        final int maxRetry = 3;
        Exception lastException = null;
        while (retryCount < maxRetry) {
            try {
                long offset = (long) partIndex * CHUNK_SIZE;
                byte[] chunkData;
                try (RandomAccessFile raf = new RandomAccessFile(localPath, "r")) {
                    raf.seek(offset);
                    int chunkSize = (int) Math.min(CHUNK_SIZE, raf.length() - offset);
                    chunkData = new byte[chunkSize];
                    raf.read(chunkData);
                }
                String uploadUrl = "https://d.pcs.baidu.com/rest/2.0/pcs/superfile2?method=upload&access_token=" + accessToken;
                String params = "type=tmpfile" +
                        "&path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8) +
                        "&uploadid=" + uploadId +
                        "&partseq=" + partIndex;
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "part_" + partIndex,
                                RequestBody.create(chunkData, MediaType.parse("application/octet-stream")))
                        .build();

                Request request = new Request.Builder()
                        .url(uploadUrl + "&" + params)
                        .post(requestBody)
                        .header("User-Agent", "pan.baidu.com")
                        .build();

                // 执行上传
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("上传分片失败: " + response.code() + " - " + response.message());
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    if (jsonResponse == null || jsonResponse.has("error_code")) {
                        throw new IOException("上传分片返回错误: " + responseBody);
                    }

                    logger.info("[百度网盘] 分片 {} 上传成功", partIndex);
                    return;
                }
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                logger.warn("[百度网盘] 分片 {} 第 {} 次上传失败，准备重试...", partIndex, retryCount, e);
                if (retryCount < maxRetry) {
                    Thread.sleep(1000L * retryCount);
                }
            }
        }
        throw new IOException("分片 " + partIndex + " 上传失败，达到最大重试次数", lastException);
    }


    public static void manageBackups(String accessToken, String remoteDir, int maxBackups) {
        logger.info("[百度网盘] 正在检查旧备份，将只保留最新的 {} 份...", maxBackups);
        try {
            // 获取目录文件列表
            String listUrl = String.format(
                    "https://pan.baidu.com/rest/2.0/xpan/file?method=list&access_token=%s&dir=%s&order=time",
                    accessToken, URLEncoder.encode(remoteDir, StandardCharsets.UTF_8));
            JsonObject response = makeApiRequest(listUrl, null);
            JsonArray fileList = response.getAsJsonArray("list");
            if (fileList == null || fileList.isEmpty()) {
                logger.info("[百度网盘] 未能在备份目录中找到任何文件或目录为空。");
                return;
            }
            ArrayList<String> filePaths = new ArrayList<>();
            for (JsonElement file : fileList) {
                String filePath = file.getAsJsonObject().get("path").getAsString();
                filePaths.add(filePath);
            }
            int numBackups = filePaths.size();
            int numToDelete = numBackups - maxBackups;
            if (numToDelete <= 0) {
                logger.info("[百度网盘] 备份文件数量 ({}) 未超限，无需清理。", numBackups);
                return;
            }
            if (filePaths.size() >= maxBackups) {
                filePaths.subList(filePaths.size() - maxBackups, filePaths.size()).clear();
            }
            if (!filePaths.isEmpty()) {
                // 执行批量删除
                String deleteUrl = String.format("https://pan.baidu.com/rest/2.0/xpan/file?method=filemanager&access_token=%s&opera=delete"
                        , accessToken);
                String deleteParams = String.format("async=2&filelist=%s", gson.toJson(filePaths));
                JsonObject deleteResp = makeApiRequest(deleteUrl, deleteParams);
                if (deleteResp.get("errno").getAsInt() > 0) {
                    throw new RuntimeException("批量删除失败: " + deleteResp);
                }
                logger.info("[百度网盘] 已成功删除旧的备份文件：");
                for (String fileName : filePaths) {
                    logger.info("  - {}", Path.of(fileName).getFileName());
                }
            }
        } catch (Exception e) {
            logger.error("[百度网盘] 管理备份文件时发生错误: {}", e.getMessage());
        }
    }

    public static String getFileMD5(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + filePath);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int length;
            while ((length = fis.read(buffer)) != -1) {
                md.update(buffer, 0, length);
            }
            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        } catch (IOException e) {
            throw new RuntimeException("读取文件时出错: " + filePath, e);
        }
    }

    private static List<String> getBlockMd5List(String path, int passNum) throws IOException {
        logger.info("(第 {} 遍读取文件...)", passNum);
        List<String> blockMd5List = new ArrayList<>();
        try (InputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] chunk = bytesRead == CHUNK_SIZE ? buffer : Arrays.copyOf(buffer, bytesRead);
                blockMd5List.add(md5Bytes(chunk));
            }
        }
        return blockMd5List;
    }

    public static String md5Bytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }
}
