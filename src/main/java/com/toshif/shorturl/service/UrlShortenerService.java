package com.toshif.shorturl.service;

import com.toshif.shorturl.config.AppProperties;
import com.toshif.shorturl.dto.ShortenRequest;
import com.toshif.shorturl.dto.ShortenResponse;
import com.toshif.shorturl.exception.ShortCodeNotFoundException;
import com.toshif.shorturl.hashing.Base62;
import com.toshif.shorturl.hashing.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;

@Service
public class UrlShortenerService {

    private final RedisShardRouter redisShardRouter;
    private final SnowflakeIdGenerator idGenerator;
    private final AppProperties appProperties;

    public UrlShortenerService(RedisShardRouter redisShardRouter,
                                SnowflakeIdGenerator idGenerator,
                                AppProperties appProperties) {
        this.redisShardRouter = redisShardRouter;
        this.idGenerator = idGenerator;
        this.appProperties = appProperties;
    }

    public ShortenResponse shorten(ShortenRequest request) {
        long id = idGenerator.nextId();
        String code = Base62.encode(id);

        if (request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
            redisShardRouter.setWithTtl(code, request.getLongUrl(), request.getTtlSeconds());
        } else {
            redisShardRouter.set(code, request.getLongUrl());
        }

        String shard = redisShardRouter.shardFor(code);
        String shortUrl = appProperties.getBaseUrl() + "/" + code;
        return new ShortenResponse(shortUrl, code, request.getLongUrl(), shard);
    }

    public String resolve(String code) {
        String longUrl = redisShardRouter.get(code);
        if (longUrl == null) {
            throw new ShortCodeNotFoundException(code);
        }
        return longUrl;
    }
}
