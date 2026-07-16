package com.ccnu.storage.service;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.storage.config.OssProperties;
import org.springframework.stereotype.Service;

import java.net.URL;
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
}