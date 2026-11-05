package com.insure.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传限额解析单测
 * <p>限额算错会直接导致「该拦的没拦」或「正常文件传不上去」,两种都是线上问题</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("上传限额")
class UploadPropertiesTest {

    private UploadProperties properties() {
        UploadProperties properties = new UploadProperties();
        properties.setMaxSize(DataSize.ofMegabytes(50));
        Map<String, DataSize> limits = new LinkedHashMap<>();
        limits.put("avatar", DataSize.ofMegabytes(2));
        limits.put("kb", DataSize.ofMegabytes(20));
        limits.put("chat-image", DataSize.ofMegabytes(10));
        // 故意配得比全局大:应被全局硬上限截断,避免某个业务把额度吃穿
        limits.put("chat-video", DataSize.ofMegabytes(80));
        properties.setBizLimits(limits);
        return properties;
    }

    @Test
    @DisplayName("头像按业务限额 2MB")
    void avatarShouldUseBizLimit() {
        assertThat(properties().limitFor("avatar", "png").toMegabytes()).isEqualTo(2);
    }

    @Test
    @DisplayName("聊天按扩展名细分:图片 10MB,视频被全局 50MB 截断")
    void chatShouldSplitByMediaType() {
        UploadProperties properties = properties();
        assertThat(properties.limitFor("chat", "jpg").toMegabytes()).isEqualTo(10);
        assertThat(properties.limitFor("chat", "mp4").toMegabytes()).isEqualTo(50);
    }

    @Test
    @DisplayName("未配置的业务回落到全局上限")
    void unknownBizShouldFallbackToGlobal() {
        assertThat(properties().limitFor("common", "pdf").toMegabytes()).isEqualTo(50);
        assertThat(properties().limitFor(null, "pdf").toMegabytes()).isEqualTo(50);
    }

    @Test
    @DisplayName("提示文案使用 MB,便于直接展示给用户")
    void limitTextShouldBeReadable() {
        UploadProperties properties = properties();
        assertThat(properties.limitText("avatar", "png")).isEqualTo("2MB");
        assertThat(properties.limitText("chat", "mp4")).isEqualTo("50MB");
        assertThat(properties.maxSizeText()).isEqualTo("50MB");
    }

    @Test
    @DisplayName("图片/视频扩展名识别")
    void shouldRecognizeMediaExtension() {
        assertThat(UploadProperties.isImage("PNG")).isTrue();
        assertThat(UploadProperties.isVideo("MP4")).isTrue();
        assertThat(UploadProperties.isVideo("pdf")).isFalse();
    }
}
