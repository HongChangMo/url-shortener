package com.urlshortener.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public Optional<String> get(String shortCode) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + shortCode));
    }

    public void put(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, TTL);
    }

    public void evict(String shortCode) {
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }
}
