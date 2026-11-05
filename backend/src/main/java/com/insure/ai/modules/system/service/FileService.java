package com.insure.ai.modules.system.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传服务(本地磁盘存储)
 *
 * @author insurance-ai
 */
public interface FileService {

    /**
     * 上传文件
     *
     * @param file    上传的文件
     * @param bizType 业务类型(avatar / kb / chat)
     * @return 文件访问地址等信息
     */
    Map<String, Object> upload(MultipartFile file, String bizType);
}
