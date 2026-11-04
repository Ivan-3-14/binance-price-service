package by.senla.binancepriceservice.service.impl;

import by.senla.binancepriceservice.exception.RateLimitException;
import by.senla.binancepriceservice.exception.ResourceNotFoundException;
import by.senla.binancepriceservice.service.PriceService;
import by.senla.binancepriceservice.client.BinanceClient;
import by.senla.binancepriceservice.client.BinanceAvgPriceResponse;
import by.senla.binancepriceservice.domain.PriceSnapshot;
import by.senla.binancepriceservice.dto.AveragePriceResponse;
import by.senla.binancepriceservice.dto.PagedPricesResponse;
import by.senla.binancepriceservice.dto.PriceResponse;
import by.senla.binancepriceservice.dto.TrendResponse;
import by.senla.binancepriceservice.dto.enums.Trend;
import by.senla.binancepriceservice.repository.PriceSnapshotRepository;
import by.senla.binancepriceservice.service.TrendCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
@Slf4j
public class PriceServiceImpl implements PriceService {

    private static final int FINANCIAL_SCALE = 10;

    private final BinanceClient binanceClient;
    private final PriceSnapshotRepository repository;
    private final TrendCalculator trendCalculator;
    private final String symbol;

    private final Duration defaultRetryAfter;

    public PriceServiceImpl(BinanceClient binanceClient,
                            PriceSnapshotRepository repository,
                            TrendCalculator trendCalculator,
                            @Value("${binance.symbol}") String symbol,
                            @Value("${binance.default-retry-after}") Duration defaultRetryAfter
    ) {
        this.binanceClient = binanceClient;
        this.repository = repository;
        this.trendCalculator = trendCalculator;
        this.symbol = symbol;
        this.defaultRetryAfter = defaultRetryAfter;
    }

    @Override
    public void fetchAndSave() {
        BinanceAvgPriceResponse response;

        try {
            response = binanceClient.fetchAvgPrice(symbol);
        } catch (HttpClientErrorException.TooManyRequests e) {
            String retryAfterHeader = e.getResponseHeaders() != null ?
                    e.getResponseHeaders().getFirst("Retry-After") : null;

            long retryAfterSeconds = defaultRetryAfter.getSeconds();

            if (retryAfterHeader != null) {
                try {
                    retryAfterSeconds = Long.parseLong(retryAfterHeader);
                } catch (NumberFormatException ex) {
                    log.warn("Failed to parse Retry-After header: {}", retryAfterHeader);
                }
            }

            retryAfterSeconds = Math.max(1, retryAfterSeconds);

            log.warn("Binance rate limit hit (429) for {}. Retry-After: {} seconds.", symbol, retryAfterSeconds);
            throw new RateLimitException(retryAfterSeconds);

        } catch (HttpClientErrorException.BadRequest e) {
            log.error("Invalid request to Binance (400). Check BINANCE_SYMBOL: {}", symbol);
            return;

        } catch (HttpClientErrorException e) {
            log.warn("Binance returned client error for {}: {}", symbol, e.getStatusCode());
            return;

        } catch (HttpServerErrorException e) {
            log.warn("Binance server error for {}: {}", symbol, e.getStatusCode());
            return;

        } catch (ResourceAccessException e) {
            log.warn("Network error while fetching price from Binance for {}: {}", symbol, e.getMessage());
            return;

        } catch (RestClientException e) {
            log.warn("Unexpected response from Binance for {}: {}", symbol, e.getMessage());
            return;
        }

        if (response == null || response.price() == null || response.price().isBlank()) {
            log.warn("Empty or malformed response from Binance for {}", symbol);
            return;
        }

        PriceSnapshot snapshot;
        try {
            snapshot = toEntity(response);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse price '{}' from Binance for {}", symbol, response.price());
            return;
        }

        repository.save(snapshot);
        log.debug("Saved {} price={} at {}", symbol, snapshot.getPrice(), snapshot.getFetchedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public PriceResponse getLatest() {
        return repository.findFirstBySymbolOrderByFetchedAtDesc(symbol)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No data for symbol " + symbol));
    }

    @Override
    @Transactional(readOnly = true)
    public AveragePriceResponse getAverage(Instant from, Instant to) {
        validateRange(from, to);
        BigDecimal avg = repository.findAveragePrice(symbol, from, to);
        if (avg == null) {
            throw new ResourceNotFoundException("No data found in the specified range");
        }
        return new AveragePriceResponse(avg.setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedPricesResponse getPage(Instant from, Instant to, int page, int size) {
        validateRange(from, to);
        if (page < 0 || size <= 0 || size > 200) {
            throw new IllegalArgumentException("Invalid pagination params");
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "fetchedAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<PriceSnapshot> result = repository.findBySymbolAndFetchedAtBetween(
                symbol, from, to, pageRequest);
        return new PagedPricesResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                page, size, result.getTotalElements(), result.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public TrendResponse getTrend(Instant from, Instant to) {
        validateRange(from, to);
        Instant mid = from.plusMillis((to.toEpochMilli() - from.toEpochMilli()) / 2);

        BigDecimal firstHalf = repository.findAveragePrice(symbol, from, mid);
        BigDecimal secondHalf = repository.findAveragePriceAfter(symbol, mid, to);

        if (firstHalf == null || secondHalf == null) {
            throw new ResourceNotFoundException("Not enough data for trend calculation in this interval");
        }

        Trend trend = trendCalculator.calculate(firstHalf, secondHalf);
        return new TrendResponse(
                symbol,
                from.atZone(ZoneOffset.UTC).toLocalDate(),
                to.atZone(ZoneOffset.UTC).toLocalDate(),
                trend);
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' are required");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
    }

    private PriceResponse toResponse(PriceSnapshot s) {
        return new PriceResponse(s.getSymbol(), s.getPrice(), s.getFetchedAt());
    }

    private PriceSnapshot toEntity(BinanceAvgPriceResponse response) {
        BigDecimal price = new BigDecimal(response.price());
        Instant fetchedAt = Instant.now();

        return PriceSnapshot.builder()
                .symbol(symbol)
                .price(price)
                .fetchedAt(fetchedAt)
                .build();
    }
}
