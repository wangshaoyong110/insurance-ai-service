package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.FileService;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.util.FileSignatureUtils;
import com.insure.ai.config.UploadProperties;
import com.insure.ai.modules.system.entity.SysFile;
import com.insure.ai.modules.system.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务(本地磁盘存储)
 * <p>
 * 上传限制统一由 {@link UploadProperties} 决定:先过全局硬上限,再过业务限额。
 * 图片与视频额外做文件头校验,防止改扩展名夹带其他内容。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 允许上传的扩展名(图片 / 文档 / 视频) */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv",
            "mp4", "mov", "m4v", "webm", "ogv");
    /** 允许的业务类型 */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of("avatar", "kb", "chat", "common");
    /** 读取文件头用于魔数校验的字节数 */
    private static final int HEADER_LENGTH = 12;
    /** 日期目录格式 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SysFileMapper sysFileMapper;
    private final UploadProperties uploadProperties;

    /**
     * 上传文件
     *
     * @param file    上传的文件
     * @param bizType 业务类型(avatar / kb / chat / common)
     * @return 文件访问地址等信息
     */
    @Override
    public Map<String, Object> upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }
        String biz = normalizeBizType(bizType);
        // 1. 全局硬上限:无论什么业务都不能突破
        DataSize globalMax = uploadProperties.getMaxSize();
        if (globalMax != null && file.getSize() > globalMax.toBytes()) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                    "文件大小超出限制(最大 " + uploadProperties.maxSizeText() + ")");
        }
        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String extension = getExtension(originalName);
        // 2. 扩展名白名单
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        // 3. 业务限额(与全局取小)
        DataSize limit = uploadProperties.limitFor(biz, extension);
        if (limit != null && file.getSize() > limit.toBytes()) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                    "文件大小超出限制," + bizDesc(biz) + "最大 " + uploadProperties.limitText(biz, extension));
        }
        // 4. 图片/视频校验文件头,避免改扩展名上传其他内容
        checkSignature(file, extension);

        String dateDir = LocalDate.now().format(DATE_DIR_FORMATTER);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = Paths.get(uploadProperties.getPath(), dateDir).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName).normalize();
        // 防止路径穿越:目标文件必须位于上传根目录下
        if (!target.startsWith(dir)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("[文件上传] 保存失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }
        String url = uploadProperties.getUrlPrefix() + dateDir + "/" + storedName;
        SysFile record = new SysFile();
        record.setOriginalName(originalName);
        record.setFileName(storedName);
        record.setFilePath(target.toString());
        record.setUrl(url);
        record.setFileSize(file.getSize());
        record.setFileType(extension);
        record.setBizType(biz);
        sysFileMapper.insert(record);

        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("id", record.getId());
        result.put("url", url);
        result.put("name", originalName);
        result.put("size", file.getSize());
        result.put("type", mediaType(extension));
        result.put("limit", uploadProperties.limitText(biz, extension));
        log.info("[文件上传] biz={} type={} size={}B url={}", biz, extension, file.getSize(), url);
        return result;
    }

    /**
     * 业务类型归一化:未传按通用处理,非法值直接拒绝,避免脏数据写进 sys_file
     */
    private String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "common";
        }
        String biz = bizType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_BIZ_TYPES.contains(biz)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的业务类型: " + bizType);
        }
        return biz;
    }

    /**
     * 校验图片/视频的真实类型
     */
    private void checkSignature(MultipartFile file, String extension) {
        if (!UploadProperties.isImage(extension) && !UploadProperties.isVideo(extension)) {
            return;
        }
        byte[] header = new byte[HEADER_LENGTH];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, HEADER_LENGTH);
            if (read < 4 || !FileSignatureUtils.matches(header, extension)) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED.getCode(),
                        "文件内容与扩展名不符,请上传真实的" + (UploadProperties.isVideo(extension) ? "视频" : "图片"));
            }
        } catch (IOException e) {
            log.error("[文件上传] 读取文件头失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * 业务类型描述(用于提示文案)
     */
    private String bizDesc(String biz) {
        return switch (biz) {
            case "avatar" -> "头像";
            case "kb" -> "知识库文档";
            case "chat" -> "聊天附件";
            default -> "文件";
        };
    }

    /**
     * 媒体大类:image / video / file
     */
    private String mediaType(String extension) {
        if (UploadProperties.isImage(extension)) {
            return "image";
        }
        return UploadProperties.isVideo(extension) ? "video" : "file";
    }

    /**
     * 获取扩展名(小写,不含点)
     */
    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
