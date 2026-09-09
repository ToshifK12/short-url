package com.toshif.shorturl.config;

import com.toshif.shorturl.hashing.SnowflakeIdGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ShardProperties.class, AppProperties.class})
public class AppConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(AppProperties appProperties) {
        return new SnowflakeIdGenerator(appProperties.getNodeId());
    }
}
