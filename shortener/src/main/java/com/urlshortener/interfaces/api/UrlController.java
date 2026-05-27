package com.urlshortener.interfaces.api;

import com.urlshortener.application.UrlService;
import com.urlshortener.interfaces.api.dto.ShortenRequest;
import com.urlshortener.interfaces.api.dto.ShortenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/data/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        String shortCode = urlService.shorten(request.originalUrl());
        return ResponseEntity.ok(new ShortenResponse(shortCode, "/api/v1/" + shortCode));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.resolveOriginalUrl(shortCode);
        urlService.incrementAccessCount(shortCode);
        return ResponseEntity.status(302).location(URI.create(originalUrl)).build();
    }
}
