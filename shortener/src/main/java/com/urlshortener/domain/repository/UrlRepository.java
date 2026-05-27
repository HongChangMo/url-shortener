package com.urlshortener.domain.repository;

import com.urlshortener.domain.Url;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UrlRepository {

    Url save(Url url);

    Url saveAndFlush(Url url);

    Optional<Url> findByShortCode(String shortCode);

    List<Url> findTopNByAccessCount(int n);

    void deleteExpired(OffsetDateTime now);

    void setShortCode(Long id, String shortCode);

    void incrementAccessCount(String shortCode, long delta);
}
