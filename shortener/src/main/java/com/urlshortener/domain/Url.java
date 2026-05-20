package com.urlshortener.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "urls")
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 10, updatable = false)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "access_count", nullable = false)
    private long accessCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    protected Url() {}

    public Url(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    public Url withAccessCount(long count) {
        this.accessCount = count;
        return this;
    }

    public Url withExpiredAt(OffsetDateTime expiredAt) {
        this.expiredAt = expiredAt;
        return this;
    }

    public boolean isExpired() {
        return expiredAt != null && expiredAt.isBefore(OffsetDateTime.now());
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getOriginalUrl() { return originalUrl; }
    public long getAccessCount() { return accessCount; }
    public OffsetDateTime getExpiredAt() { return expiredAt; }
}
