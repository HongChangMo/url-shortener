package com.urlshortener.interfaces.api;

import com.urlshortener.interfaces.api.dto.ShortenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UrlApiE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port", valkey::getFirstMappedPort);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shorten_then_redirect_fullFlow() {
        // shorten
        ResponseEntity<Map> shortenRes = restTemplate.postForEntity(
                "/api/v1/data/shorten",
                new ShortenRequest("https://example.com"),
                Map.class
        );
        assertThat(shortenRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String shortCode = (String) shortenRes.getBody().get("shortCode");
        assertThat(shortCode).isNotBlank();

        // redirect
        ResponseEntity<Void> redirectRes = restTemplate.getForEntity(
                "/api/v1/" + shortCode, Void.class
        );
        assertThat(redirectRes.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectRes.getHeaders().getLocation()).hasToString("https://example.com");
    }

    @Test
    void redirect_unknownCode_returns404() {
        ResponseEntity<Void> res = restTemplate.getForEntity("/api/v1/unknown999", Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
