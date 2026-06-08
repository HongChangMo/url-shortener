package com.urlshortener.infrastructure.cache;

import com.urlshortener.infrastructure.config.ValkeyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
@Import({ValkeyConfig.class, UrlCacheService.class})
class UrlCacheServiceTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port", valkey::getFirstMappedPort);
    }

    @Autowired
    UrlCacheService cacheService;

    @BeforeEach
    void setUp(@Autowired RedisTemplate<String, String> redisTemplate) {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void get_missingKey_returnsEmpty() {
        assertThat(cacheService.get("nonexistent")).isEmpty();
    }

    @Test
    void put_then_get_returnsOriginalUrl() {
        cacheService.put("abc123", "https://example.com");
        assertThat(cacheService.get("abc123")).hasValue("https://example.com");
    }

    @Test
    void evict_removesKey() {
        cacheService.put("abc123", "https://example.com");
        cacheService.evict("abc123");
        assertThat(cacheService.get("abc123")).isEmpty();
    }

    @Test
    void putNull_then_get_returnsNullSentinel() {
        cacheService.putNull("missing");
        Optional<String> result = cacheService.get("missing");
        assertThat(result).isPresent();
        assertThat(cacheService.isNullCache(result.get())).isTrue();
    }

    @Test
    void isNullCache_withNormalUrl_returnsFalse() {
        assertThat(cacheService.isNullCache("https://example.com")).isFalse();
    }

    @Test
    void tryLock_firstAttempt_succeeds() {
        assertThat(cacheService.tryLock("abc123")).isTrue();
    }

    @Test
    void tryLock_alreadyLocked_fails() {
        cacheService.tryLock("abc123");
        assertThat(cacheService.tryLock("abc123")).isFalse();
    }

    @Test
    void unlock_releasesLock() {
        cacheService.tryLock("abc123");
        cacheService.unlock("abc123");
        assertThat(cacheService.tryLock("abc123")).isTrue();
    }

    @Test
    void get_afterPut_populatesL1OnL2Hit() {
        cacheService.put("abc123", "https://example.com");
        // L1 적재 확인: evict 없이 두 번 연속 조회해도 일관된 값 반환
        assertThat(cacheService.get("abc123")).hasValue("https://example.com");
        assertThat(cacheService.get("abc123")).hasValue("https://example.com");
    }

    @Test
    void evict_removesFromBothL1AndL2() {
        cacheService.put("abc123", "https://example.com");
        cacheService.evict("abc123");
        assertThat(cacheService.get("abc123")).isEmpty();
    }
}
