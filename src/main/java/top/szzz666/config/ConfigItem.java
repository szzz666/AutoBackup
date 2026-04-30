package top.szzz666.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置项注解，用于标记配置字段并添加注释
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigItem {
    /**
     * 配置项的键名，默认使用字段名
     */
    String key() default "";

    /**
     * 配置项的注释说明
     */
    String comment() default "";
}