package by.senla.binancepriceservice.service.impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceServiceImpl implements PriceService {

    private static final BigDecimal TREND_FLAT_THRESHOLD = new BigDecimal("0.001");
    private static final int FINANCIAL_SCALE = 10;

    private final BinanceClient binanceClient;
    private final PriceSnapshotRepository repository;

    @Value("${binance.symbol}")
    private String symbol;

    @Override
    public void fetchAndSave() {
        try {
            BinanceAvgPriceResponse response = binanceClient.fetchAvgPrice(symbol);
            if (response == null || response.price() == null) {
                log.warn("Empty response from Binance for {}", symbol);
                return;
            }
            BigDecimal price = new BigDecimal(response.price());
            Instant fetchedAt = Instant.now();
            PriceSnapshot snapshot = PriceSnapshot.builder()
                    .symbol(symbol)
                    .price(price)
                    .fetchedAt(fetchedAt)
                    .build();
            PriceSnapshot saved = repository.save(snapshot);
            log.info("Saved {} price={} at {}", symbol, price, fetchedAt);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Binance rate limit hit (429), skipping this tick");
        } catch (RestClientResponseException e) {
            log.warn("Binance returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unexpected error while fetching Binance price", e);
        }
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
        Page<PriceSnapshot> result = repository.findBySymbolAndFetchedAtBetween(
                symbol, from, to, PageRequest.of(page, size));
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
        BigDecimal secondHalf = repository.findAveragePrice(symbol, mid, to);
        if (firstHalf == null || secondHalf == null) {
            throw new ResourceNotFoundException("Not enough data for trend calculation in this interval");
        }
        Trend trend = calculateTrend(firstHalf, secondHalf);
        return new TrendResponse(
                symbol,
                from.atZone(ZoneOffset.UTC).toLocalDate(),
                to.atZone(ZoneOffset.UTC).toLocalDate(),
                trend);
    }

    private Trend calculateTrend(BigDecimal first, BigDecimal second) {
        BigDecimal diff = second.subtract(first);
        BigDecimal threshold = first.abs().multiply(TREND_FLAT_THRESHOLD);
        if (diff.abs().compareTo(threshold) <= 0) {
            return Trend.FLAT;
        }
        return diff.signum() > 0 ? Trend.UPWARD : Trend.DOWNWARD;
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
    }

    private PriceResponse toResponse(PriceSnapshot s) {
        return new PriceResponse(s.getSymbol(), s.getPrice(), s.getFetchedAt());
    }
}
