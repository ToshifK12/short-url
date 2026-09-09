package com.toshif.shorturl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the list of independent Redis shards that make up the horizontally
 * scaled cache/storage tier, plus the number of virtual nodes each physical
 * shard gets on the consistent-hash ring.
 *
 * application.yml:
 * shorturl:
 *   redis:
 *     shards:
 *       - host: redis-node-1
 *         port: 6379
 *       - host: redis-node-2
 *         port: 6379
 *     virtual-nodes-per-shard: 150
 */
@ConfigurationProperties(prefix = "shorturl.redis")
public class ShardProperties {

    private List<Shard> shards;
    private int virtualNodesPerShard = 150;

    public List<Shard> getShards() {
        return shards;
    }

    public void setShards(List<Shard> shards) {
        this.shards = shards;
    }

    public int getVirtualNodesPerShard() {
        return virtualNodesPerShard;
    }

    public void setVirtualNodesPerShard(int virtualNodesPerShard) {
        this.virtualNodesPerShard = virtualNodesPerShard;
    }

    public static class Shard {
        private String host;
        private int port = 6379;
        private int poolSize = 64;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
