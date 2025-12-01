package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.szu.afternoon3.platform.config.AliyunOssProperties;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.service.AliyunOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Date;

@Service
@Slf4j
@ConditionalOnProperty(name = "aliyun.oss.enable", havingValue = "true", matchIfMissing = true)
public class AliyunOssServiceImpl implements AliyunOssService {

    @Autowired
    private AliyunOssProperties ossProperties;

    @Override
    public String uploadFile(MultipartFile file) {
        // 1. 基础校验
        if (file == null || file.isEmpty()) {
            throw new AppException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }

        // 2. 准备 OSS 参数
        String endpoint = ossProperties.getEndpoint();
        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();
        String bucketName = ossProperties.getBucketName();

        // 3. 构建文件路径: avatar/2023/11/02/uuid.jpg
        // 获取原始后缀
        String originalFilename = file.getOriginalFilename();
        String extension = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(extension)) {
            extension = "png"; // 默认兜底
        }

        // 生成文件名
        String fileName = UUID.randomUUID().toString(true) + "." + extension;
        // 按日期分类文件夹
        String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
        // 最终 objectName
        String objectName = "uploads/" + datePath + "/" + fileName;

        // 4. 创建 OSSClient 实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 获取流
            InputStream inputStream = file.getInputStream();
            // 执行上传
            ossClient.putObject(bucketName, objectName, inputStream);

            // 5. 拼接返回 URL
            // 格式: https://{bucket}.{endpoint}/{objectName}
            return "https://" + bucketName + "." + endpoint + "/" + objectName;

        } catch (Exception e) {
            log.error("OSS文件上传失败", e);
            throw new AppException(ResultCode.OSS_UPLOAD_ERROR);
        } finally {
            // 关闭 Client
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}