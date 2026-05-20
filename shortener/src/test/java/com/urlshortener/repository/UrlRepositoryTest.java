package com.urlshortener.repository;

import com.urlshortener.domain.Url;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UrlRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UrlRepository repository;

    @Test
    void findByShortCode_returnsUrl() {
        Url url = new Url("abc123", "https://example.com");
        repository.save(url);

        Optional<Url> found = repository.findByShortCode("abc123");

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void findTopByAccessCount_returnsOrdered() {
        repository.save(new Url("a", "https://a.com").withAccessCount(100));
        repository.save(new Url("b", "https://b.com").withAccessCount(50));

        List<Url> top1 = repository.findTopNByAccessCount(1);
        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).getShortCode()).isEqualTo("a");

        List<Url> top2 = repository.findTopNByAccessCount(2);
        assertThat(top2).hasSize(2);
    }

    @Test
    void deleteExpired_removesExpiredUrls() {
        Url expired = new Url("exp", "https://expired.com")
            .withExpiredAt(OffsetDateTime.now().minusDays(1));
        Url active = new Url("active", "https://active.com");
        repository.save(expired);
        repository.save(active);

        repository.deleteExpired(OffsetDateTime.now());

        assertThat(repository.findByShortCode("exp")).isEmpty();
        assertThat(repository.findByShortCode("active")).isPresent();
    }
}
