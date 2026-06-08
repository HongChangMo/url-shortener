package com.urlshortener.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";
    private static final String LOCK_PREFIX = "lock:url:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final String NULL_VALUE = "__NULL__";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${shortener.null-cache-ttl-seconds:300}")
    private long nullCacheTtlSeconds;

    @Value("${shortener.l1-cache-size:1000}")
    private int l1CacheSize;

    @Value("${shortener.l1-cache-ttl-seconds:60}")
    private long l1CacheTtlSeconds;

    @Value("${shortener.population-lock-ttl-seconds:3}")
    private long lockTtlSeconds;

    private Cache<String, String> l1Cache;

    @PostConstruct
    void initL1Cache() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(l1CacheSize)
                .expireAfterWrite(l1CacheTtlSeconds, TimeUnit.SECONDS)
                .build();
    }

    public Optional<String> get(String shortCode) {
        String l1Value = l1Cache.getIfPresent(shortCode);
        if (l1Value != null) {
            return Optional.of(l1Value);
        }
        String l2Value = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        if (l2Value != null) {
            l1Cache.put(shortCode, l2Value);
            return Optional.of(l2Value);
        }
        return Optional.empty();
    }

    public boolean isNullCache(String value) {
        return NULL_VALUE.equals(value);
    }

    public void put(String shortCode, String originalUrl) {
        l1Cache.put(shortCode, originalUrl);
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, TTL);
    }

    public void putNull(String shortCode) {
        l1Cache.put(shortCode, NULL_VALUE);
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, NULL_VALUE, Duration.ofSeconds(nullCacheTtlSeconds));
    }

    public void evict(String shortCode) {
        l1Cache.invalidate(shortCode);
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }

    public boolean tryLock(String shortCode) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + shortCode, "1", Duration.ofSeconds(lockTtlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String shortCode) {
        redisTemplate.delete(LOCK_PREFIX + shortCode);
    }
}
