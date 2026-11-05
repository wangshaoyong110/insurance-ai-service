package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.FileService;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.modules.system.entity.SysFile;
import com.insure.ai.modules.system.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 允许上传的扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv");
    /** 单文件最大 20MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    /** 日期目录格式 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SysFileMapper sysFileMapper;

    @Value("${insure.upload.path}")
    private String uploadPath;

    @Value("${insure.upload.url-prefix}")
    private String urlPrefix;

    /**
     * 上传文件
     *
     * @param file    上传的文件
     * @param bizType 业务类型(avatar / kb / chat)
     * @return 文件访问地址等信息
     */
    @Override
    public Map<String, Object> upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        }
        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String dateDir = LocalDate.now().format(DATE_DIR_FORMATTER);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = Paths.get(uploadPath, dateDir).toAbsolutePath().normalize();
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
        String url = urlPrefix + dateDir + "/" + storedName;
        SysFile record = new SysFile();
        record.setOriginalName(originalName);
        record.setFileName(storedName);
        record.setFilePath(target.toString());
        record.setUrl(url);
        record.setFileSize(file.getSize());
        record.setFileType(extension);
        record.setBizType(StringUtils.hasText(bizType) ? bizType : "common");
        sysFileMapper.insert(record);

        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("id", record.getId());
        result.put("url", url);
        result.put("name", originalName);
        result.put("size", file.getSize());
        return result;
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
