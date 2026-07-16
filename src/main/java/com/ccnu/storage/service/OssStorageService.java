package com.ccnu.storage.service;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.storage.config.OssProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

/**
 * 阿里云 OSS 存储服务。
 *
 * <p>提供预签名 PUT URL 生成，前端直传 OSS，节省服务端带宽。</p>
 */
@Service
public class OssStorageService {

    private final OssProperties props;

    public OssStorageService(OssProperties props) {
        this.props = props;
    }

    /**
     * 生成 PUT 预签名 URL，前端用此 URL 直传文件到 OSS。
     *
     * @param objectKey         OSS 对象键
     * @param contentType       上传文件的 Content-Type
     * @param expiresInSeconds  签名有效期（秒）
     * @return 预签名 URL 字符串
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expiresInSeconds) {
        ensureConfigured();
        OSS client = new OSSClientBuilder().build(
                props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            Date expiration = new Date(System.currentTimeMillis() + expiresInSeconds * 1000L);
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                    props.getBucket(), objectKey, HttpMethod.PUT);
            req.setExpiration(expiration);
            if (contentType != null && !contentType.isBlank()) {
                req.setContentType(contentType);
            }
            URL url = client.generatePresignedUrl(req);
            return url.toString();
        } finally {
            client.shutdown();
        }
    }

    private void ensureConfigured() {
        if (props.getEndpoint() == null || props.getAccessKeyId() == null
                || props.getAccessKeySecret() == null || props.getBucket() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "对象存储未配置");
        }
    }

    /** 上传头像文件到 OSS，返回公开 URL。 */
    public String uploadAvatar(long userId, MultipartFile file) {
        ensureConfigured();
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) ext = original.substring(original.lastIndexOf('.'));
        String objectKey = props.getFolder() + "/avatars/" + userId + "-" + Instant.now().toEpochMilli() + ext;
        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            client.putObject(new PutObjectRequest(props.getBucket(), objectKey, file.getInputStream()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        } finally {
            client.shutdown();
        }
        String domain = props.getPublicDomain();
        if (domain != null && !domain.isBlank()) return domain.replaceAll("/$", "") + "/" + objectKey;
        return "https://" + props.getBucket() + "." + props.getEndpoint() + "/" + objectKey;
    }
}