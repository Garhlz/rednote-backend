package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.AliyunOssService;
import com.szu.afternoon3.platform.vo.UrlVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 公共控制器
 * 处理文件上传等通用业务
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    @Autowired
    private AliyunOssService aliyunOssService;

    /**
     * 通用文件上传接口
     * @param file 文件流
     * @return 文件URL
     */
    @PostMapping("/upload")
    @OperationLog(module = "公共模块", description = "上传文件")
    public Result<UrlVO> uploadFile(@RequestParam("file") MultipartFile file) {
        String url = aliyunOssService.uploadFile(file);

        return Result.success(new UrlVO(url));
    }
}