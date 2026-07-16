package com.ccnu.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    private String publicDomain;
    private String folder;
}