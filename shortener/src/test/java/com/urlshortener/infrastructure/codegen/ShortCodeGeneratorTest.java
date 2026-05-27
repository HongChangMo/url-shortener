package com.urlshortener.infrastructure.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ShortCodeGenerator();
    }

    @Test
    void generate_producesNonBlankCode() {
        String code = generator.generate(1L);
        assertThat(code).isNotBlank();
    }

    @Test
    void generate_sameIdProducesSameCode() {
        assertThat(generator.generate(42L)).isEqualTo(generator.generate(42L));
    }

    @Test
    void generate_differentIdsProduceDifferentCodes() {
        assertThat(generator.generate(1L)).isNotEqualTo(generator.generate(2L));
    }

    @Test
    void generate_codeHasMinimumLength() {
        String code = generator.generate(1L);
        assertThat(code.length()).isGreaterThanOrEqualTo(6);
    }
}
