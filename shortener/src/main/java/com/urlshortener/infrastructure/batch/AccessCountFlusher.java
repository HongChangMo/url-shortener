package com.urlshortener.infrastructure.batch;

import com.urlshortener.domain.repository.UrlRepository;
import com.urlshortener.infrastructure.cache.HitCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessCountFlusher {

    private final HitCounterService hitCounterService;
    private final UrlRepository urlRepository;

    @Scheduled(fixedRateString = "${shortener.access-count-flush-rate-ms:60000}")
    @Transactional
    public void flush() {
        Map<String, Long> hits = hitCounterService.drainAll();
        if (hits.isEmpty()) {
            return;
        }
        hits.forEach(urlRepository::incrementAccessCount);
    }
}
