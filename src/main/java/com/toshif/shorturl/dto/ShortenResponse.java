package com.toshif.shorturl.dto;

public class ShortenResponse {

    private final String shortUrl;
    private final String shortCode;
    private final String longUrl;
    private final String shard;

    public ShortenResponse(String shortUrl, String shortCode, String longUrl, String shard) {
        this.shortUrl = shortUrl;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.shard = shard;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getShard() {
        return shard;
    }
}
