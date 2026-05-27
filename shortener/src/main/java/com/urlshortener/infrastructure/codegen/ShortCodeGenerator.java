package com.urlshortener.infrastructure.codegen;

import org.sqids.Sqids;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShortCodeGenerator {

    private final Sqids sqids;

    public ShortCodeGenerator() {
        this.sqids = Sqids.builder()
                .minLength(6)
                .build();
    }

    public String generate(long id) {
        return sqids.encode(List.of(id));
    }
}
