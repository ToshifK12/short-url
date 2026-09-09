package com.toshif.shorturl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shorturl:
 *   node-id: 1          # unique per app instance (0-1023), used by the ID generator
 *   base-url: http://localhost:8080
 */
@ConfigurationProperties(prefix = "shorturl")
public class AppProperties {

    private long nodeId = 0;
    private String baseUrl = "http://localhost:8080";

    public long getNodeId() {
        return nodeId;
    }

    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
