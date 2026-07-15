package com.ccnu.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置。
 *
 * <p>扫描 com.ccnu 下所有模块的 mapper 包，各 Mapper 接口无需单独加 @Mapper。</p>
 */
@Configuration
@MapperScan("com.ccnu.**.mapper")
public class MyBatisConfig {
}