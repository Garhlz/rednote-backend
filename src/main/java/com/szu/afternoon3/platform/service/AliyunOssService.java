package com.szu.afternoon3.platform.service;

import org.springframework.web.multipart.MultipartFile;

public interface AliyunOssService {
    /**
     * 上传文件
     * @param file 前端传来的文件对象
     * @return 文件的完整访问URL
     */
    String uploadFile(MultipartFile file);
}