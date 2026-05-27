package com.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String shortCode) {
        super("URL expired: " + shortCode);
    }
}
