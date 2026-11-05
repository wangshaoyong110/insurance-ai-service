package com.insure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文件上传统一配置
 * <p>
 * 上传限制分两层:全局硬上限 {@code max-size}(任何业务都不能突破),
 * 以及按业务细分的 {@code biz-limits}(头像要小、知识库文档可以大、聊天视频居中)。
 * 实际生效的是两者中更小的那个,避免某个业务把全局额度吃满。
 * </p>
 * <p>
 * 配置示例(application.yml):
 * <pre>
 * insure:
 *   upload:
 *     path: ./upload/
 *     url-prefix: /api/upload/
 *     max-size: 50MB
 *     biz-limits:
 *       avatar: 2MB
 *       kb: 20MB
 *       chat-image: 10MB
 *       chat-video: 50MB
 * </pre>
 *
 * @author shaoyong.wang
 */
@Data
@Component
@ConfigurationProperties(prefix = "insure.upload")
public class UploadProperties {

    /** 本地存储根目录 */
    private String path = "./upload/";

    /** 访问前缀,与 WebMvcConfig 中的静态资源映射保持一致 */
    private String urlPrefix = "/api/upload/";

    /** 全局单文件硬上限 */
    private DataSize maxSize = DataSize.ofMegabytes(50);

    /** 分业务限额,key 见 {@link #limitFor} */
    private Map<String, DataSize> bizLimits = new LinkedHashMap<>();

    /** 图片扩展名(与 FileServiceImpl 的白名单保持一致) */
    private static final java.util.Set<String> IMAGE_EXTENSIONS =
            java.util.Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 视频扩展名 */
    private static final java.util.Set<String> VIDEO_EXTENSIONS =
            java.util.Set.of("mp4", "mov", "m4v", "webm", "ogv");

    /**
     * 解析某个业务 + 扩展名实际可用的大小上限
     *
     * @param bizType   业务类型:avatar / kb / chat / common
     * @param extension 文件扩展名(不带点)
     * @return 生效上限(全局与业务限额取小)
     */
    public DataSize limitFor(String bizType, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String key = resolveKey(bizType, ext);
        DataSize bizLimit = bizLimits.get(key);
        if (bizLimit == null) {
            return maxSize;
        }
        return bizLimit.toBytes() < maxSize.toBytes() ? bizLimit : maxSize;
    }

    /**
     * 业务 key 归一化:聊天按图片/视频再细分
     */
    private String resolveKey(String bizType, String extension) {
        String biz = bizType == null || bizType.isBlank() ? "common" : bizType.trim().toLowerCase(Locale.ROOT);
        if ("chat".equals(biz)) {
            if (VIDEO_EXTENSIONS.contains(extension)) {
                return "chat-video";
            }
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return "chat-image";
            }
        }
        return biz;
    }

    /**
     * 全局上限的可读文本(用于错误提示)
     */
    public String maxSizeText() {
        long bytes = maxSize.toBytes();
        return bytes >= 1024 * 1024
                ? (bytes / (1024 * 1024)) + "MB"
                : Math.max(1, bytes / 1024) + "KB";
    }

    /**
     * 指定业务上限的可读文本
     */
    public String limitText(String bizType, String extension) {
        long bytes = limitFor(bizType, extension).toBytes();
        return bytes >= 1024 * 1024
                ? (bytes / (1024 * 1024)) + "MB"
                : Math.max(1, bytes / 1024) + "KB";
    }

    /**
     * 扩展名是否为图片
     */
    public static boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension == null ? "" : extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 扩展名是否为视频
     */
    public static boolean isVideo(String extension) {
        return VIDEO_EXTENSIONS.contains(extension == null ? "" : extension.toLowerCase(Locale.ROOT));
    }
}
