package com.urlshortener.infrastructure.batch;

import com.urlshortener.domain.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class ExpiredUrlCleaner {

    private final UrlRepository urlRepository;

    @Scheduled(cron = "${shortener.expired-url-cleanup-cron:0 0 0 * * *}")
    @Transactional
    public void clean() {
        urlRepository.deleteExpired(OffsetDateTime.now());
    }
}
