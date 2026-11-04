package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.modules.system.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器
 *
 * @author insurance-ai
 */
@Tag(name = "通用-文件上传", description = "图片/文档上传与访问")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件(登录用户可用)
     *
     * @param file    文件
     * @param bizType 业务类型:avatar / kb / chat
     */
    @Log(module = "文件管理", operation = "上传文件")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String bizType) {
        return Result.success("上传成功", fileService.upload(file, bizType));
    }
}
