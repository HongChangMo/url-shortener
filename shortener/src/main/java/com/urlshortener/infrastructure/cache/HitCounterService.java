package com.urlshortener.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HitCounterService {

    private static final String HITS_KEY = "url:hits";
    private static final String FLUSH_KEY = "url:hits:flushing";

    private final RedisTemplate<String, String> redisTemplate;

    public void increment(String shortCode) {
        redisTemplate.opsForZSet().incrementScore(HITS_KEY, shortCode, 1.0);
    }

    /**
     * HITS_KEY를 원자적으로 FLUSH_KEY로 교체해 드레인한다.
     * RENAME 이후 새로 들어오는 increment는 새 HITS_KEY에 쌓이므로 유실이 없다.
     */
    public Map<String, Long> drainAll() {
        try {
            redisTemplate.rename(HITS_KEY, FLUSH_KEY);
        } catch (Exception e) {
            return Map.of(); // HITS_KEY 없으면 처리할 것 없음
        }

        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(FLUSH_KEY, 0, -1);
        redisTemplate.delete(FLUSH_KEY);

        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }

        return entries.stream()
                .filter(e -> e.getValue() != null && e.getScore() != null)
                .collect(Collectors.toMap(
                        ZSetOperations.TypedTuple::getValue,
                        e -> e.getScore().longValue()
                ));
    }
}
