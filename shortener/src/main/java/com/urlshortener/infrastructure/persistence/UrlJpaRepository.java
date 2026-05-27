package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UrlJpaRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    @Query("SELECT u FROM Url u ORDER BY u.accessCount DESC LIMIT :n")
    List<Url> findTopNByAccessCount(@Param("n") int n);

    @Modifying
    @Transactional
    @Query("DELETE FROM Url u WHERE u.expiredAt IS NOT NULL AND u.expiredAt < :now")
    void deleteExpired(@Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE Url u SET u.shortCode = :shortCode WHERE u.id = :id")
    void setShortCode(@Param("id") Long id, @Param("shortCode") String shortCode);

    @Modifying
    @Transactional
    @Query("UPDATE Url u SET u.accessCount = u.accessCount + :delta WHERE u.shortCode = :shortCode")
    void incrementAccessCount(@Param("shortCode") String shortCode, @Param("delta") long delta);
}
