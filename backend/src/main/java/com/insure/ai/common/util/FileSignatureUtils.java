package com.insure.ai.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文件头校验
 * <p>
 * 只靠扩展名判断类型并不可靠:把 exe 改名成 .jpg 一样能上传成功,
 * 落在可访问的静态目录里就可能变成投递恶意文件的入口。
 * 这里对图片与视频做一次魔数校验,至少保证"声称是图片/视频的必须是真图片/视频"。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class FileSignatureUtils {

    private FileSignatureUtils() {
    }

    /**
     * 校验文件头是否与扩展名匹配
     *
     * @param header    文件头字节(建议前 12 个字节)
     * @param extension 扩展名(小写,不含点)
     * @return true-匹配;非图片/视频扩展名一律返回 true(由扩展名白名单兜底)
     */
    public static boolean matches(byte[] header, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
            case "bmp" -> startsWith(header, 0x42, 0x4D);
            case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46) && textAt(header, 8, 4).equals("WEBP");
            case "mp4", "m4v", "mov" -> textAt(header, 4, 4).equals("ftyp");
            case "webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            case "ogv" -> startsWith(header, 0x4F, 0x67, 0x67, 0x53);
            default -> true;
        };
    }

    /**
     * 校验前缀字节
     */
    private static boolean startsWith(byte[] header, int... expected) {
        if (header == null || header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取指定偏移处的 ASCII 文本
     */
    private static String textAt(byte[] header, int offset, int length) {
        if (header == null || header.length < offset + length) {
            return "";
        }
        return new String(header, offset, length, StandardCharsets.US_ASCII);
    }
}
