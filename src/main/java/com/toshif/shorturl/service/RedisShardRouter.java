package com.toshif.shorturl.service;

import com.toshif.shorturl.config.ShardProperties;
import com.toshif.shorturl.hashing.ConsistentHashRing;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns one {@link JedisPool} per physical Redis shard and uses a
 * {@link ConsistentHashRing} to decide which shard a given short code lives
 * on. This is the piece that makes the cache/storage tier horizontally
 * scalable: shards can be added behind a config change and only the keys
 * that hash past the new shard's ring position move, instead of a full
 * re-shard.
 */
@Component
public class RedisShardRouter {

    private static final Logger log = LoggerFactory.getLogger(RedisShardRouter.class);

    private final Map<String, JedisPool> pools = new LinkedHashMap<>();
    private final ConsistentHashRing<String> ring;

    public RedisShardRouter(ShardProperties shardProperties) {
        if (shardProperties.getShards() == null || shardProperties.getShards().isEmpty()) {
            throw new IllegalStateException("At least one Redis shard must be configured under shorturl.redis.shards");
        }

        this.ring = new ConsistentHashRing<>(shardProperties.getVirtualNodesPerShard());

        for (ShardProperties.Shard shard : shardProperties.getShards()) {
            String shardId = shard.getHost() + ":" + shard.getPort();
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(shard.getPoolSize());
            poolConfig.setMaxIdle(shard.getPoolSize());
            poolConfig.setMinIdle(Math.min(8, shard.getPoolSize()));
            poolConfig.setTestOnBorrow(false);
            poolConfig.setTestWhileIdle(true);

            JedisPool pool = new JedisPool(poolConfig, shard.getHost(), shard.getPort());
            pools.put(shardId, pool);
            ring.addNode(shardId);
            log.info("Registered Redis shard {} on the consistent hash ring", shardId);
        }
    }

    /** Which shard a key belongs to, without touching the network. */
    public String shardFor(String key) {
        return ring.route(key);
    }

    public void set(String key, String value) {
        String shardId = ring.route(key);
        try (Jedis jedis = pools.get(shardId).getResource()) {
            jedis.set(key, value);
        }
    }

    public void setWithTtl(String key, String value, long ttlSeconds) {
        String shardId = ring.route(key);
        try (Jedis jedis = pools.get(shardId).getResource()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    public String get(String key) {
        String shardId = ring.route(key);
        try (Jedis jedis = pools.get(shardId).getResource()) {
            return jedis.get(key);
        }
    }

    public boolean exists(String key) {
        String shardId = ring.route(key);
        try (Jedis jedis = pools.get(shardId).getResource()) {
            return jedis.exists(key);
        }
    }

    public long incrBy(String key, long delta) {
        String shardId = ring.route(key);
        try (Jedis jedis = pools.get(shardId).getResource()) {
            return jedis.incrBy(key, delta);
        }
    }

    public int shardCount() {
        return pools.size();
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(JedisPool::close);
    }
}
