package com.urlshortener.application;

import com.urlshortener.domain.Url;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.domain.repository.UrlRepository;
import com.urlshortener.infrastructure.cache.UrlCacheService;
import com.urlshortener.infrastructure.codegen.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UrlService {

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlCacheService cacheService;

    @Transactional
    public String shorten(String originalUrl) {
        Url url = urlRepository.saveAndFlush(Url.builder().originalUrl(originalUrl).build());
        String shortCode = shortCodeGenerator.generate(url.getId());
        urlRepository.setShortCode(url.getId(), shortCode);
        cacheService.put(shortCode, originalUrl);
        return shortCode;
    }

    public String resolveOriginalUrl(String shortCode) {
        return cacheService.get(shortCode)
                .orElseGet(() -> {
                    Url url = urlRepository.findByShortCode(shortCode)
                            .orElseThrow(() -> new UrlNotFoundException(shortCode));
                    if (url.isExpired()) {
                        throw new UrlExpiredException(shortCode);
                    }
                    cacheService.put(shortCode, url.getOriginalUrl());
                    return url.getOriginalUrl();
                });
    }

    @Transactional
    public void incrementAccessCount(String shortCode) {
        urlRepository.incrementAccessCount(shortCode, 1L);
    }
}
