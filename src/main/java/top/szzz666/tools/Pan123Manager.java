package top.szzz666.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kotlin.Pair;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static top.szzz666.Main.config;
import static top.szzz666.tools.BaiduPanManager.*;

public class Pan123Manager {
    private static final Logger logger = LoggerFactory.getLogger(Pan123Manager.class);

    public static String getAccessToken() throws Exception {
        HashMap<String, Object> tokenData = config.get("123pan_token");
        try {
            if (tokenData != null) {
                int expiresAt = Integer.parseInt(tokenData.get("expiredAt").toString());
                int now = (int) (System.currentTimeMillis() / 1000);
                if (expiresAt > now) {
                    logger.info("[123云盘] 使用本地 access_token");
                    return (String) tokenData.get("accessToken");
                } else {
                    logger.info("[123云盘] access_token 已过期，尝试刷新...");
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return authorizeNewToken();
    }

    private static void SaveToken(JsonObject data) {
        String accessToken = data.get("accessToken").getAsString();
        String expiredAt = data.get("expiredAt").getAsString();
        HashMap<String, Object> token = new HashMap<>();
        token.put("accessToken", accessToken);
        token.put("expiredAt", convertIsoToTimestamp(expiredAt) - 300);
        config.set("123pan_token", token);
        logger.info("[123云盘] 保存 access_token 成功。");
    }

    private static String authorizeNewToken() throws Exception {
        String url = "https://open-api.123pan.com/api/v1/access_token";
        String formData = String.format("clientID=%s&clientSecret=%s",
                config.getString("123云盘ClientID"), config.getString("123云盘ClientSecret"));
        JsonObject data = makeApiRequest0(url, formData).get("data").getAsJsonObject();
        if (data.has("accessToken")) {
            logger.info("[123云盘] 授权成功，正在保存 access_token...");
            SaveToken(data);
            return data.get("accessToken").getAsString();
        }
        logger.error("[123云盘] 获取 access_token 失败: {}", data);
        Thread.sleep(3000);
        return getAccessToken();
    }

    private static JsonObject makeApiRequest0(String url, String formData) throws Exception {
        Request request = new Request.Builder().url(url).post(RequestBody.create(formData, MediaType.parse("application/json"))).addHeader("Content-Type", "application/json").addHeader("Platform", "open_platform").build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("意外代码 {}", response);
            }
            String responseBody = null;
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            logger.debug(responseBody);
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    private static JsonObject makeApiRequest(String url, String formData, String accessToken) throws Exception {
        Request request;
        if (formData == null) {
            request = new Request.Builder().url(url).get().addHeader("Content-Type", "application/json").addHeader("Platform", "open_platform").addHeader("Authorization", "Bearer " + accessToken).build();
        } else {
            request = new Request.Builder().url(url).post(RequestBody.create(formData, MediaType.parse("application/json"))).addHeader("Content-Type", "application/json").addHeader("Platform", "open_platform").addHeader("Authorization", "Bearer " + accessToken).build();
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
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }


    public static void uploadLargeFile(String localPath, String remotePath) throws Exception {
        String accessToken = getAccessToken();
        Path path = Path.of(localPath);
        if (!Files.exists(path)) {
            logger.info("[123云盘] 开始上传文件: {}", path.getFileName());
            return;
        }
        // 获取文件大小
        long fileSize = Files.size(path);
        logger.info("[123云盘] 文件大小: {} MB", fileSize / 1024.0 / 1024.0);

        // ===== 预上传阶段 =====
        logger.info("[123云盘] 正在进行预上传...");
        String precreateUrl = "https://open-api.123pan.com/upload/v2/file/create";
        JsonObject jsonData = new JsonObject();
        jsonData.addProperty("filename", remotePath);
        jsonData.addProperty("size", fileSize);
        jsonData.addProperty("parentFileID", 0);
        jsonData.addProperty("containDir", true);
        jsonData.addProperty("etag", getFileMD5(localPath));
        String formData = jsonData.toString();
//        logger.info(formData);
        JsonObject precreateResp = makeApiRequest(precreateUrl, formData, accessToken).get("data").getAsJsonObject();
        if (precreateResp.get("reuse").getAsBoolean()) {
            logger.info("[123云盘] 秒传成功");
        } else {
            String preuploadID = precreateResp.get("preuploadID").getAsString();
            int sliceSize = precreateResp.get("sliceSize").getAsInt();
            String uploadUrl = precreateResp.get("servers").getAsJsonArray().get(0).getAsString();
            if (preuploadID == null) throw new RuntimeException("预上传失败: " + precreateResp);
            logger.info("[123云盘] 预上传成功");
            // ===== 文件校验阶段 =====
            logger.info("[123云盘] 正在执行文件完整性预上传自检...");
            List<String> blockMd5ListPass1 = getBlockMd5List(localPath, 1, sliceSize);
            List<String> blockMd5ListPass2 = getBlockMd5List(localPath, 2, sliceSize);
            if (!blockMd5ListPass1.equals(blockMd5ListPass2)) {
                throw new RuntimeException("本地文件读取不一致，可能存在硬件问题。");
            }
            logger.info("[123云盘] 文件完整性自检通过。文件在本地是稳定可读的。");
            int totalParts = blockMd5ListPass1.size();
            logger.info("[123云盘] 文件校验信息计算完成，共 {} 个分块。", totalParts);
            // ===== 分片上传阶段 =====
            logger.info("[123云盘] 开始分片上传...");
            for (int idx = 0; idx < totalParts; idx++) {
                // 实际分片上传
                uploadPart(preuploadID, idx, localPath, sliceSize, uploadUrl, accessToken);
            }
            logger.info("[123云盘] 所有分片上传完毕。");
            // ===== 合并文件阶段 =====
            logger.info("[123云盘] 正在合并文件...");
            String createUrl = "https://open-api.123pan.com/upload/v2/file/upload_complete";
            String createFormData = "{\"preuploadID\": \"" + preuploadID + "\"}";
            int retryCount = 0;
            final int maxRetry = 3;
            while (retryCount < maxRetry) {
                try {
                    JsonObject createResp = makeApiRequest(createUrl, createFormData, accessToken);
                    int code = createResp.get("code").getAsInt();
                    JsonObject data = createResp.get("data").getAsJsonObject();
                    if (code == 0 && data.get("completed").getAsBoolean()) {
                        int fileId = data.get("fileID").getAsInt();
                        UpFileId = fileId;
                        logger.info("[123云盘] 文件上传成功, 文件ID: {}", fileId);
                        return;
                    } else {
                        throw new RuntimeException("合并文件失败: " + createResp);
                    }
                } catch (Exception e) {
                    retryCount++;
                    logger.warn("[123云盘] 合并文件失败，准备第 {} 次重试...", retryCount);
                    if (retryCount < maxRetry) {
                        Thread.sleep(1000L * retryCount);
                    }
                }
            }

        }
    }

    private static Integer UpFileId = null;

    private static void uploadPart(String uploadId, int partIndex, String localPath, int sliceSize, String uploadUrl, String accessToken) throws Exception {
        int retryCount = 0;
        final int maxRetry = 3;
        Exception lastException = null;
        while (retryCount < maxRetry) {
            try {
                long offset = (long) partIndex * sliceSize;
                byte[] chunkData;
                try (RandomAccessFile raf = new RandomAccessFile(localPath, "r")) {
                    raf.seek(offset);
                    int chunkSize = (int) Math.min(sliceSize, raf.length() - offset);
                    chunkData = new byte[chunkSize];
                    raf.read(chunkData);
                }
                int sliceNo = partIndex + 1;
                RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("preuploadID", uploadId).addFormDataPart("sliceNo", String.valueOf(sliceNo)).addFormDataPart("sliceMD5", md5Bytes(chunkData)).addFormDataPart("slice", localPath, RequestBody.create(MediaType.parse("application/octet-stream"), chunkData)).build();
                Request request = new Request.Builder().url(uploadUrl + "/upload/v2/file/slice").method("POST", body).addHeader("Authorization", "Bearer " + Pan123Manager.getAccessToken()).addHeader("Platform", "open_platform").build();
                // 执行上传
                try (Response response = httpClient.newCall(request).execute()) {

                    if (!response.isSuccessful()) {
                        throw new IOException("上传分片失败: " + response.code() + " - " + response.message());
                    }
                    String responseBody = null;
                    if (response.body() != null) {
                        responseBody = response.body().string();
                    }
//                    logger.info(responseBody);
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    if (jsonResponse == null || jsonResponse.get("code").getAsInt() != 0) {
                        throw new IOException("上传分片返回错误: " + responseBody);
                    }
                    logger.info("[123云盘] 分片 {} 上传成功", sliceNo);
                    return;
                }
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                logger.warn("[123云盘] 分片 {} 第 {} 次上传失败，准备重试...", partIndex, retryCount, e);
                if (retryCount < maxRetry) {
                    Thread.sleep(1000L * retryCount);
                }
            }
        }
        throw new IOException("分片 " + partIndex + " 上传失败，达到最大重试次数", lastException);
    }

    public static void manageBackups(int maxBackups) throws Exception {
        String accessToken = getAccessToken();
        logger.info("[123云盘] 正在检查旧备份，将只保留最新的 {} 份...", maxBackups);
        try {
            if (UpFileId == null) {
                throw new RuntimeException("尚未上传文件，无法获取父目录ID");
            }

            String parentFileIDUrl = String.format("https://open-api.123pan.com/api/v1/file/detail?fileID=%d",
                    UpFileId);
            int parentFileID = makeApiRequest(parentFileIDUrl, null, accessToken).get("data").getAsJsonObject().get("parentFileID").getAsInt();
            String listUrl = String.format("https://open-api.123pan.com/api/v2/file/list?parentFileId=%d&limit=100",
                    parentFileID);
            JsonArray fileList = makeApiRequest(listUrl, null, accessToken).get("data").getAsJsonObject().get("fileList").getAsJsonArray();
            int fileListSize = fileList.size();
            if (fileListSize > maxBackups) {
                List<Pair<Integer, Integer>> filesWithTime = new ArrayList<>();
                for (JsonElement file : fileList) {
                    String createAt = file.getAsJsonObject().get("createAt").getAsString();
                    Integer fileID = file.getAsJsonObject().get("fileId").getAsInt();
                    int time = convertToTimestamp(createAt);
                    filesWithTime.add(new Pair<>(fileID, time));
                }
                filesWithTime.sort(Comparator.comparingInt(Pair::getSecond));
                if (filesWithTime.size() >= maxBackups) {
                    filesWithTime.subList(filesWithTime.size() - maxBackups, filesWithTime.size()).clear();
                }
                List<Integer> fileIds = new ArrayList<>();
                for (Pair<Integer, Integer> file : filesWithTime) {
                    fileIds.add(file.getFirst());
                }
                String trashUrl = "https://open-api.123pan.com/api/v1/file/trash";
                String deleteUrl = "https://open-api.123pan.com/api/v1/file/delete";
                JsonObject trashJson = new JsonObject();
                JsonObject deleteJson = new JsonObject();
                trashJson.add("fileIds", gson.toJsonTree(fileIds));
                deleteJson.add("fileIds", gson.toJsonTree(fileIds));
                String trashParams = trashJson.toString();
                String deleteParams = deleteJson.toString();
//                logger.info(trashParams);
                int code = makeApiRequest(trashUrl, trashParams, accessToken).get("code").getAsInt();
                int code1 = makeApiRequest(deleteUrl, deleteParams, accessToken).get("code").getAsInt();
                if (code == 0 && code1 == 0) {
                    logger.info("[123云盘] 已成功删除旧的备份文件：");
                    for (JsonElement file : fileList) {
                        String fileName = file.getAsJsonObject().get("filename").getAsString();
                        Integer fileID = file.getAsJsonObject().get("fileId").getAsInt();
                        if (fileIds.contains(fileID)) {
                            logger.info("  - {}", fileName);
                        }
                    }
                    return;
                }
                throw new RuntimeException("删除旧备份文件失败: " + trashParams);
            } else {
                logger.info("[123云盘] 备份文件数量 ({}) 未超限，无需清理。", fileListSize);
            }
        } catch (Exception e) {
            logger.error("[123云盘] 管理备份文件时发生错误: {}", e.getMessage());
        }
    }


    public static int convertIsoToTimestamp(String isoDateString) {
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(isoDateString);
            return (int) zonedDateTime.toEpochSecond();
        } catch (DateTimeParseException e) {
            logger.error("日期格式错误，请输入ISO 8601格式的日期（如'2025-03-23T15:48:37+08:00'）");
            return -1;
        }
    }

    public static int convertToTimestamp(String dateString) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);
            return (int) dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (DateTimeParseException e) {
            logger.error("日期格式错误，请输入'yyyy-MM-dd HH:mm:ss'格式的日期");
            return -1;
        }
    }

    private static List<String> getBlockMd5List(String path, int passNum, int sliceSize) throws IOException {
        logger.info("(第 {} 遍读取文件...)", passNum);
        List<String> blockMd5List = new ArrayList<>();
        try (InputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[sliceSize];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] chunk = bytesRead == sliceSize ? buffer : Arrays.copyOf(buffer, bytesRead);
                blockMd5List.add(md5Bytes(chunk));
            }
        }
        return blockMd5List;
    }

}
