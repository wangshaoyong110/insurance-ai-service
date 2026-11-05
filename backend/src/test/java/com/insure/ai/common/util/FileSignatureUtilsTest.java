package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件头校验单测
 * <p>改名绕过扩展名白名单是最常见的上传绕过手法,这里锁死图片/视频的魔数匹配</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("文件头校验")
class FileSignatureUtilsTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};

    @Test
    @DisplayName("真实图片/视频通过校验")
    void shouldAcceptRealMedia() {
        assertThat(FileSignatureUtils.matches(PNG, "png")).isTrue();
        assertThat(FileSignatureUtils.matches(JPG, "jpeg")).isTrue();
        assertThat(FileSignatureUtils.matches(MP4, "mp4")).isTrue();
        assertThat(FileSignatureUtils.matches(WEBM, "webm")).isTrue();
    }

    @Test
    @DisplayName("扩展名与内容不符时拒绝")
    void shouldRejectMismatchedContent() {
        assertThat(FileSignatureUtils.matches(PNG, "mp4")).isFalse();
        assertThat(FileSignatureUtils.matches(MP4, "png")).isFalse();
        // 把任意文本改成 .jpg 上传,文件头对不上
        assertThat(FileSignatureUtils.matches("hello world!".getBytes(StandardCharsets.UTF_8), "jpg")).isFalse();
    }

    @Test
    @DisplayName("非图片/视频扩展名不做魔数限制,交给扩展名白名单")
    void shouldSkipOtherExtensions() {
        assertThat(FileSignatureUtils.matches("%PDF-1.7".getBytes(StandardCharsets.UTF_8), "pdf")).isTrue();
    }
}
