package by.senla.binancepriceservice.scheduler;

import by.senla.binancepriceservice.exception.RateLimitException;
import by.senla.binancepriceservice.service.PriceService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@Profile("!test")
public class PriceScheduler {

    private final PriceService priceService;
    private final TaskScheduler taskScheduler;
    private final Duration defaultInterval;

    private ScheduledFuture<?> scheduledFuture;

    public PriceScheduler(PriceService priceService,
                          TaskScheduler taskScheduler,
                          @Value("${binance.poll-interval}") Duration defaultInterval) {
        this.priceService = priceService;
        this.taskScheduler = taskScheduler;
        this.defaultInterval = defaultInterval;
    }

    @PostConstruct
    public void start() {
        scheduleNext(defaultInterval);
    }

    public void scheduleNext(Duration delay) {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }

        log.info("Scheduling next Binance fetch in {} seconds", delay.getSeconds());

        scheduledFuture = taskScheduler.schedule(
                this::poll,
                Instant.now().plus(delay)
        );
    }

    public void poll() {
        log.debug("Scheduler tick");
        try {
            priceService.fetchAndSave();
            scheduleNext(defaultInterval);
        } catch (RateLimitException e) {

            long waitSeconds = Math.min(e.getRetryAfterSeconds(), 300);
            Duration waitTime = Duration.ofSeconds(waitSeconds);
            log.warn("Rate limit backoff: scheduling next run in {} seconds", waitSeconds);
            scheduleNext(waitTime);
        }catch (Exception e) {
            log.error("Unexpected error in scheduler", e);
            scheduleNext(defaultInterval);
        }
    }
}
