package com.urlshortener.application;

import com.urlshortener.domain.Url;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.domain.repository.UrlRepository;
import com.urlshortener.infrastructure.cache.HitCounterService;
import com.urlshortener.infrastructure.cache.UrlCacheService;
import com.urlshortener.infrastructure.codegen.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock UrlRepository urlRepository;
    @Mock ShortCodeGenerator shortCodeGenerator;
    @Mock UrlCacheService cacheService;
    @Mock HitCounterService hitCounterService;

    @InjectMocks UrlService urlService;

    @Test
    void shorten_savesUrlAndReturnsShortCode() {
        Url saved = urlWithId(1L, "https://example.com");
        when(urlRepository.saveAndFlush(any())).thenReturn(saved);
        when(shortCodeGenerator.generate(1L)).thenReturn("abc123");

        String result = urlService.shorten("https://example.com");

        assertThat(result).isEqualTo("abc123");
        verify(urlRepository).setShortCode(1L, "abc123");
        verify(cacheService).put("abc123", "https://example.com");
    }

    @Test
    void resolveOriginalUrl_l1CacheHit_returnsCachedUrl() {
        when(cacheService.get("abc123")).thenReturn(Optional.of("https://example.com"));

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(urlRepository);
        verify(cacheService, never()).tryLock(any());
    }

    @Test
    void resolveOriginalUrl_negativeCacheHit_throwsWithoutDbQuery() {
        when(cacheService.get("missing")).thenReturn(Optional.of("__NULL__"));
        when(cacheService.isNullCache("__NULL__")).thenReturn(true);

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);
        verifyNoInteractions(urlRepository);
        verify(cacheService, never()).tryLock(any());
    }

    @Test
    void resolveOriginalUrl_cacheMiss_lockAcquired_queriesDbAndCaches() {
        when(cacheService.get("abc123"))
                .thenReturn(Optional.empty())   // 최초 확인
                .thenReturn(Optional.empty());  // 락 획득 후 더블 체크
        when(cacheService.tryLock("abc123")).thenReturn(true);
        Url url = Url.builder().shortCode("abc123").originalUrl("https://example.com").build();
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verify(cacheService).put("abc123", "https://example.com");
        verify(cacheService).unlock("abc123");
    }

    @Test
    void resolveOriginalUrl_lockAcquired_doubleCheckHit_skipsDb() {
        when(cacheService.get("abc123"))
                .thenReturn(Optional.empty())                           // 최초 확인
                .thenReturn(Optional.of("https://example.com"));       // 더블 체크 hit
        when(cacheService.tryLock("abc123")).thenReturn(true);

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(urlRepository);
        verify(cacheService).unlock("abc123");
    }

    @Test
    void resolveOriginalUrl_lockNotAcquired_retriesUntilCachePopulated() {
        when(cacheService.get("abc123"))
                .thenReturn(Optional.empty())                           // 최초 확인
                .thenReturn(Optional.empty())                           // 재시도 1
                .thenReturn(Optional.of("https://example.com"));       // 재시도 2 hit
        when(cacheService.tryLock("abc123")).thenReturn(false);

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(urlRepository);
    }

    @Test
    void resolveOriginalUrl_lockNotAcquired_allRetriesFail_fallsBackToDb() {
        when(cacheService.get("abc123")).thenReturn(Optional.empty());
        when(cacheService.tryLock("abc123")).thenReturn(false);
        Url url = Url.builder().shortCode("abc123").originalUrl("https://example.com").build();
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verify(cacheService).put("abc123", "https://example.com");
    }

    @Test
    void resolveOriginalUrl_notFound_storesNullCacheAndThrows() {
        when(cacheService.get("missing"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(cacheService.tryLock("missing")).thenReturn(true);
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);
        verify(cacheService).putNull("missing");
        verify(cacheService).unlock("missing");
    }

    @Test
    void resolveOriginalUrl_expired_throwsUrlExpiredException() {
        when(cacheService.get("exp"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(cacheService.tryLock("exp")).thenReturn(true);
        Url expired = Url.builder()
                .shortCode("exp")
                .originalUrl("https://example.com")
                .expiredAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(urlRepository.findByShortCode("exp")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("exp"))
                .isInstanceOf(UrlExpiredException.class);
        verify(cacheService).unlock("exp");
    }

    @Test
    void incrementAccessCount_delegatesToHitCounter() {
        urlService.incrementAccessCount("abc123");
        verify(hitCounterService).increment("abc123");
        verifyNoInteractions(urlRepository);
    }

    private Url urlWithId(Long id, String originalUrl) {
        Url url = Url.builder().originalUrl(originalUrl).build();
        try {
            Field f = Url.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(url, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return url;
    }
}
