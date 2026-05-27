package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.Url;
import com.urlshortener.domain.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UrlRepositoryImpl implements UrlRepository {

    private final UrlJpaRepository jpaRepository;

    @Override
    public Url save(Url url) {
        return jpaRepository.save(url);
    }

    @Override
    public Url saveAndFlush(Url url) {
        return jpaRepository.saveAndFlush(url);
    }

    @Override
    public Optional<Url> findByShortCode(String shortCode) {
        return jpaRepository.findByShortCode(shortCode);
    }

    @Override
    public List<Url> findTopNByAccessCount(int n) {
        return jpaRepository.findTopNByAccessCount(n);
    }

    @Override
    public void deleteExpired(OffsetDateTime now) {
        jpaRepository.deleteExpired(now);
    }

    @Override
    public void setShortCode(Long id, String shortCode) {
        jpaRepository.setShortCode(id, shortCode);
    }

    @Override
    public void incrementAccessCount(String shortCode, long delta) {
        jpaRepository.incrementAccessCount(shortCode, delta);
    }
}
