package by.senla.binancepriceservice.scheduler;

import by.senla.binancepriceservice.exception.RateLimitException;
import by.senla.binancepriceservice.service.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceSchedulerTest {

    @Mock
    private PriceService priceService;

    @Mock
    private TaskScheduler taskScheduler;

    private PriceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PriceScheduler(priceService, taskScheduler, Duration.ofSeconds(30));
    }

    @Test
    void pollRescheduleWithBackoffOnRateLimit() {
        doThrow(new RateLimitException(60)).when(priceService).fetchAndSave();

        Instant before = Instant.now();
        scheduler.poll();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(), captor.capture());
        Instant scheduledTime = captor.getValue();

        assertThat(scheduledTime).isAfter(before.plusSeconds(59));
        assertThat(scheduledTime).isBefore(after.plusSeconds(61));
    }

    @Test
    void pollDelegateToServiceAndRescheduleWithDefaultInterval() {
        scheduler.poll();

        verify(priceService).fetchAndSave();
        verify(taskScheduler).schedule(any(), any(Instant.class));
    }

    @Test
    void pollRescheduleWithDefaultIntervalOnUnexpectedError() {
        doThrow(new RuntimeException("Unexpected")).when(priceService).fetchAndSave();

        scheduler.poll();

        verify(taskScheduler).schedule(any(), any(Instant.class));
    }
}
