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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
@Import({ValkeyConfig.class, HitCounterService.class})
class HitCounterServiceTest {

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
    HitCounterService hitCounterService;

    @BeforeEach
    void setUp(@Autowired RedisTemplate<String, String> redisTemplate) {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void increment_accumulatesScore() {
        hitCounterService.increment("abc123");
        hitCounterService.increment("abc123");
        hitCounterService.increment("def456");

        Map<String, Long> hits = hitCounterService.drainAll();
        assertThat(hits.get("abc123")).isEqualTo(2L);
        assertThat(hits.get("def456")).isEqualTo(1L);
    }

    @Test
    void drainAll_clearsKey() {
        hitCounterService.increment("abc123");
        hitCounterService.drainAll();

        assertThat(hitCounterService.drainAll()).isEmpty();
    }

    @Test
    void drainAll_whenEmpty_returnsEmptyMap() {
        assertThat(hitCounterService.drainAll()).isEmpty();
    }

    @Test
    void drainAll_doesNotLoseIncrementsAddedAfterDrain() {
        hitCounterService.increment("abc123");
        Map<String, Long> first = hitCounterService.drainAll();

        // 드레인 이후 새로 들어온 increment는 유실되지 않음
        hitCounterService.increment("abc123");
        Map<String, Long> second = hitCounterService.drainAll();

        assertThat(first.get("abc123")).isEqualTo(1L);
        assertThat(second.get("abc123")).isEqualTo(1L);
    }
}
