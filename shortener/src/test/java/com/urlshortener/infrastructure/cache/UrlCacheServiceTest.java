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
}
