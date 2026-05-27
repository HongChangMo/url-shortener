package com.urlshortener.application;

import com.urlshortener.domain.Url;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.domain.repository.UrlRepository;
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
    void resolveOriginalUrl_cacheHit_returnsCachedUrl() {
        when(cacheService.get("abc123")).thenReturn(Optional.of("https://example.com"));

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(urlRepository);
    }

    @Test
    void resolveOriginalUrl_cacheMiss_queriesDbAndCaches() {
        when(cacheService.get("abc123")).thenReturn(Optional.empty());
        Url url = Url.builder().shortCode("abc123").originalUrl("https://example.com").build();
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        String result = urlService.resolveOriginalUrl("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verify(cacheService).put("abc123", "https://example.com");
    }

    @Test
    void resolveOriginalUrl_notFound_throwsUrlNotFoundException() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveOriginalUrl_expired_throwsUrlExpiredException() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        Url expired = Url.builder()
                .shortCode("exp")
                .originalUrl("https://example.com")
                .expiredAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(urlRepository.findByShortCode("exp")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("exp"))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void incrementAccessCount_delegatesToRepository() {
        urlService.incrementAccessCount("abc123");
        verify(urlRepository).incrementAccessCount("abc123", 1L);
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
