package com.urlshortener.infrastructure.batch;

import com.urlshortener.domain.repository.UrlRepository;
import com.urlshortener.infrastructure.cache.HitCounterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessCountFlusherTest {

    @Mock HitCounterService hitCounterService;
    @Mock UrlRepository urlRepository;

    @InjectMocks AccessCountFlusher flusher;

    @Test
    void flush_whenHitsExist_updatesDbForEachCode() {
        when(hitCounterService.drainAll()).thenReturn(Map.of("abc123", 5L, "def456", 3L));

        flusher.flush();

        verify(urlRepository).incrementAccessCount("abc123", 5L);
        verify(urlRepository).incrementAccessCount("def456", 3L);
    }

    @Test
    void flush_whenNoHits_skipsDbUpdate() {
        when(hitCounterService.drainAll()).thenReturn(Map.of());

        flusher.flush();

        verifyNoInteractions(urlRepository);
    }
}
