package com.philosophers.philosopher_service.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "philosopher.mode", havingValue = "zookeeper")
public class ZookeeperConfig {

    @Bean(initMethod = "start", destroyMethod = "close")
    public CuratorFramework curatorFramework(
            @Value("${zookeeper.connect-string:localhost:2181}") String connectString,
            @Value("${zookeeper.session-timeout-ms:15000}") int sessionTimeoutMs,
            @Value("${zookeeper.connection-timeout-ms:5000}") int connectionTimeoutMs) {
        return CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(sessionTimeoutMs)
                .connectionTimeoutMs(connectionTimeoutMs)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
    }
}
