package by.senla.binancepriceservice.scheduler;

import by.senla.binancepriceservice.service.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceSchedulerTest {

    @Mock
    private PriceService priceService;

    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private PriceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PriceScheduler(priceService, taskScheduler, Duration.ofSeconds(30));
    }

    @Test
    void poll_shouldDelegateToService() {
        scheduler.poll();
        verify(priceService).fetchAndSave();
    }
}
