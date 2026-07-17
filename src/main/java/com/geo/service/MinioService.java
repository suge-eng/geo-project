package com.geo.service;

import com.geo.config.MinioConfig;
import com.geo.common.BusinessException;
import com.geo.common.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public MinioService(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("创建 MinIO Bucket: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("创建 Bucket 失败", e);
        }
    }

    public String uploadFile(MultipartFile file) {
        String filename = generateFilename(file.getOriginalFilename());
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filename)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return buildPublicUrl(filename);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件上传失败");
        }
    }

    public String uploadBytes(byte[] data, String filename, String contentType) {
        String storedFilename = generateFilename(filename);
        try (InputStream is = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storedFilename)
                    .stream(is, data.length, -1)
                    .contentType(contentType)
                    .build());
            return buildPublicUrl(storedFilename);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件上传失败");
        }
    }

    public void deleteFile(String url) {
        String filename = extractFilename(url);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filename)
                    .build());
        } catch (Exception e) {
            log.error("删除文件失败: {}", url, e);
        }
    }

    private String generateFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "screenshots/" + datePath + "/" + uuid + extension;
    }

    private String buildPublicUrl(String filename) {
        String endpoint = minioConfig.getPublicEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = minioConfig.getEndpoint();
        }
        return endpoint + "/" + minioConfig.getBucket() + "/" + filename;
    }

    private String extractFilename(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String bucket = minioConfig.getBucket() + "/";
        int index = url.indexOf(bucket);
        if (index > 0) {
            return url.substring(index + bucket.length());
        }
        return url;
    }
}