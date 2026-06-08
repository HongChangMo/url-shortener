package com.urlshortener.application;

import com.urlshortener.domain.Url;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.domain.repository.UrlRepository;
import com.urlshortener.infrastructure.cache.HitCounterService;
import com.urlshortener.infrastructure.cache.UrlCacheService;
import com.urlshortener.infrastructure.codegen.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UrlService {

    private static final int LOCK_RETRY_COUNT = 3;
    private static final long LOCK_RETRY_MILLIS = 50L;

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlCacheService cacheService;
    private final HitCounterService hitCounterService;

    @Transactional
    public String shorten(String originalUrl) {
        Url url = urlRepository.saveAndFlush(Url.builder().originalUrl(originalUrl).build());
        String shortCode = shortCodeGenerator.generate(url.getId());
        urlRepository.setShortCode(url.getId(), shortCode);
        cacheService.put(shortCode, originalUrl);
        return shortCode;
    }

    public String resolveOriginalUrl(String shortCode) {
        Optional<String> cached = cacheService.get(shortCode);
        if (cached.isPresent()) {
            return unwrap(shortCode, cached.get());
        }

        if (cacheService.tryLock(shortCode)) {
            try {
                // 락 획득 후 다른 인스턴스가 이미 적재했을 수 있으므로 재확인
                Optional<String> afterLock = cacheService.get(shortCode);
                if (afterLock.isPresent()) {
                    return unwrap(shortCode, afterLock.get());
                }
                return loadFromDb(shortCode);
            } finally {
                cacheService.unlock(shortCode);
            }
        }

        // 락 획득 실패: 락 보유 인스턴스가 캐시를 적재할 때까지 대기 후 재시도
        for (int i = 0; i < LOCK_RETRY_COUNT; i++) {
            try {
                Thread.sleep(LOCK_RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Optional<String> retry = cacheService.get(shortCode);
            if (retry.isPresent()) {
                return unwrap(shortCode, retry.get());
            }
        }

        // 폴백: 락 보유자가 제한 시간 내 적재하지 못한 경우 직접 DB 조회
        return loadFromDb(shortCode);
    }

    public void incrementAccessCount(String shortCode) {
        hitCounterService.increment(shortCode);
    }

    private String unwrap(String shortCode, String value) {
        if (cacheService.isNullCache(value)) {
            throw new UrlNotFoundException(shortCode);
        }
        return value;
    }

    private String loadFromDb(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    cacheService.putNull(shortCode);
                    return new UrlNotFoundException(shortCode);
                });
        if (url.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }
        cacheService.put(shortCode, url.getOriginalUrl());
        return url.getOriginalUrl();
    }
}
