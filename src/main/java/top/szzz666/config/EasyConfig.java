package top.szzz666.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class EasyConfig {

    private final File file;
    private final Yaml yaml;
    /** 配置数据，嵌套Map结构 */
    private Map<String, Object> data = new LinkedHashMap<>();
    /** 存储注解信息 */
    private final Map<String, ItemMeta> items = new LinkedHashMap<>();

    private static class ItemMeta {
        final String key;
        final String comment;
        final Field field;
        final Object defaultValue;

        ItemMeta(String key, String comment, Field field, Object defaultValue) {
            this.key = key;
            this.comment = comment;
            this.field = field;
            this.defaultValue = defaultValue;
        }
    }

    public EasyConfig(String filePath) {
        this.file = new File(filePath);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        this.yaml = new Yaml(options);
    }

    /**
     * 从带 @ConfigItem 注解的类中收集配置项定义
     */
    public void loadFromClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            ConfigItem anno = field.getAnnotation(ConfigItem.class);
            if (anno == null) continue;

            String key = anno.key().isEmpty() ? field.getName() : anno.key();
            String comment = anno.comment();
            Object defaultValue = null;
            try {
                field.setAccessible(true);
                defaultValue = field.get(null);
            } catch (IllegalAccessException ignored) {
            }
            items.put(key, new ItemMeta(key, comment, field, defaultValue));
        }
    }

    /**
     * 加载配置文件：读取已有值，缺失项用默认值填充，保存并回写字段
     */
    public void load() {
        // 读取已有配置文件
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Map<String, Object> loaded = yaml.load(fis);
                if (loaded != null) {
                    data = loaded;
                }
            } catch (Exception ignored) {
            }
        } else {
            // 确保父目录存在
            file.getParentFile().mkdirs();
        }

        // 用默认值填充缺失项
        for (ItemMeta meta : items.values()) {
            if (!exists(meta.key)) {
                setNestedValue(data, meta.key, meta.defaultValue);
            }
        }

        // 保存到文件（不带注释）
        save();

        // 插入注释
        saveWithComments();

        // 回写到静态字段
        for (ItemMeta meta : items.values()) {
            Object value = getNestedValue(data, meta.key);
            if (value == null) value = meta.defaultValue;
            try {
                meta.field.setAccessible(true);
                meta.field.set(null, convertType(value, meta.field.getType(), meta.defaultValue));
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        load();
    }

    // ==================== 嵌套key操作 ====================

    /**
     * 检查点分级key是否存在
     */
    private boolean exists(String key) {
        return getNestedValue(data, key) != null;
    }

    /**
     * 获取点分级key的值
     * 如 database.jdbcUrl → data.get("database").get("jdbcUrl")
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String key) {
        String[] parts = key.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    /**
     * 设置点分级key的值，自动创建中间嵌套Map
     * 如 database.jdbcUrl → {database: {jdbcUrl: value}}
     */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.get(parts[i]);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<>();
                current.put(parts[i], child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }

    // ==================== 类型转换 ====================

    private Object convertType(Object value, Class<?> type, Object defaultValue) {
        if (value == null) return defaultValue;
        if (type.isAssignableFrom(value.getClass())) return value;
        try {
            String str = value.toString();
            if (type == String.class) return str;
            if (type == int.class || type == Integer.class) return Integer.parseInt(str);
            if (type == double.class || type == Double.class) return Double.parseDouble(str);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(str);
            if (type == long.class || type == Long.class) return Long.parseLong(str);
            if (type == float.class || type == Float.class) return Float.parseFloat(str);
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    // ==================== 读取方法 ====================

    public String getString(String key) {
        return getString(key, "");
    }

    public String getString(String key, String defaultValue) {
        Object val = getNestedValue(data, key);
        return val != null ? val.toString() : defaultValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        Object val = getNestedValue(data, key);
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return val != null ? Integer.parseInt(val.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        Object val = getNestedValue(data, key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return val != null ? Double.parseDouble(val.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = getNestedValue(data, key);
        if (val instanceof Boolean) return (Boolean) val;
        return val != null ? Boolean.parseBoolean(val.toString()) : defaultValue;
    }

    public Object get(String key) {
        return getNestedValue(data, key);
    }

    // ==================== 写入方法 ====================

    public void set(String key, Object value) {
        setNestedValue(data, key, value);
    }

    /**
     * 保存配置到文件（不带注释）
     */
    public void save() {
        try {
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                yaml.dump(data, writer);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 带注释保存 ====================

    /**
     * 保存配置文件并在对应key上方添加注释
     */
    private void saveWithComments() {
        if (items.isEmpty()) return;

        // 构建 key -> comment 映射
        Map<String, String> commentMap = new LinkedHashMap<>();
        for (ItemMeta meta : items.values()) {
            if (meta.comment != null && !meta.comment.isEmpty()) {
                commentMap.put(meta.key, meta.comment);
            }
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> result = new ArrayList<>();

            // 跟踪缩进层级对应的key路径
            String[] levels = new String[32];

            for (String line : lines) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    result.add(line);
                    continue;
                }

                // 计算缩进层级（2空格为一级）
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else break;
                }
                int level = indent / 2;

                // 提取当前行的key名
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx <= 0) {
                    result.add(line);
                    continue;
                }
                String keyName = trimmed.substring(0, colonIdx).trim();

                // 更新层级路径
                levels[level] = keyName;
                // 清除更深层级（回退时）
                for (int i = level + 1; i < levels.length; i++) levels[i] = null;

                // 构建完整key路径
                StringBuilder keyPath = new StringBuilder();
                for (int i = 0; i <= level; i++) {
                    if (levels[i] == null) break;
                    if (keyPath.length() > 0) keyPath.append(".");
                    keyPath.append(levels[i]);
                }
                String fullKey = keyPath.toString();

                // 检查是否需要添加注释（避免重复添加）
                String comment = commentMap.get(fullKey);
                if (comment != null) {
                    String indentStr = line.substring(0, indent);
                    // 检查上一行是否已有相同注释
                    boolean alreadyHasComment = false;
                    if (!result.isEmpty()) {
                        String lastLine = result.get(result.size() - 1);
                        if (lastLine.trim().equals("# " + comment)) {
                            alreadyHasComment = true;
                        }
                    }
                    if (!alreadyHasComment) {
                        result.add(indentStr + "# " + comment);
                    }
                }

                result.add(line);
            }

            Files.write(file.toPath(), result);
        } catch (Exception ignored) {
        }
    }
}
