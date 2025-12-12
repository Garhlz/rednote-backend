package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.AliyunOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Date;

/**
 * 本地文件存储实现 (Mock OSS)
 * 当配置文件中 aliyun.oss.enable = false 时生效
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "aliyun.oss.enable", havingValue = "false")
public class LocalFileServiceImpl implements AliyunOssService {

    @Value("${file.upload.path}")
    private String localPath;

    @Value("${file.upload.base-url}")
    private String baseUrl;

    @Override
    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        try {
            // 1. 生成文件名
            String originalFilename = file.getOriginalFilename();
            String extension = StrUtil.subAfter(originalFilename, ".", true);
            String fileName = UUID.randomUUID().toString(true) + "." + (extension == null ? "png" : extension);

            // 2. 生成日期目录 (例如: uploads/2023/11/02/)
            String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");

            // 3. 拼接完整的本地磁盘路径
            // user.dir 是项目根目录
            String projectRoot = System.getProperty("user.dir");
            String fullDir = projectRoot + File.separator + localPath + File.separator + datePath;

            // 确保目录存在
            if (!FileUtil.exist(fullDir)) {
                FileUtil.mkdir(fullDir);
            }

            // 4. 保存文件到本地
            File dest = new File(fullDir + File.separator + fileName);
            file.transferTo(dest);
            log.info("本地文件保存成功: {}", dest.getAbsolutePath());

            // 5. 返回可访问的 URL
            // 格式: http://localhost:8080/uploads/2023/11/02/xxx.jpg
            return baseUrl + datePath + "/" + fileName;

        } catch (Exception e) {
            log.error("本地文件上传失败", e);
            throw new AppException(ResultCode.SYSTEM_ERROR, "本地存储失败");
        }
    }
}