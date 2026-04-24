package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 高德地图配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "amap")
public class AmapConfig {
    /**
     * 高德地图Web服务Key
     */
    private String key;

    private String webKey;

    private String securityJsCode;
}
