package by.senla.binancepriceservice.scheduler;

import by.senla.binancepriceservice.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceScheduler {

    private final PriceService priceService;

    @Scheduled(fixedDelayString = "${binance.poll-interval}")
    public void poll() {
        log.debug("Scheduler tick");
        priceService.fetchAndSave();
    }
}
