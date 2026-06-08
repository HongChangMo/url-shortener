package com.urlshortener.infrastructure.batch;

import com.urlshortener.domain.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiredUrlCleanerTest {

    @Mock UrlRepository urlRepository;

    @InjectMocks ExpiredUrlCleaner cleaner;

    @Test
    void clean_callsDeleteExpiredWithCurrentTime() {
        OffsetDateTime before = OffsetDateTime.now();

        cleaner.clean();

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(urlRepository).deleteExpired(captor.capture());

        OffsetDateTime after = OffsetDateTime.now();
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
