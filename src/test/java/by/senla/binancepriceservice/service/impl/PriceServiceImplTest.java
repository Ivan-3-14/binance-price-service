package by.senla.binancepriceservice.service.impl;

import by.senla.binancepriceservice.client.BinanceAvgPriceResponse;
import by.senla.binancepriceservice.client.BinanceClient;
import by.senla.binancepriceservice.domain.PriceSnapshot;
import by.senla.binancepriceservice.dto.AveragePriceResponse;
import by.senla.binancepriceservice.dto.PagedPricesResponse;
import by.senla.binancepriceservice.dto.PriceResponse;
import by.senla.binancepriceservice.dto.TrendResponse;
import by.senla.binancepriceservice.dto.enums.Trend;
import by.senla.binancepriceservice.exception.RateLimitException;
import by.senla.binancepriceservice.exception.ResourceNotFoundException;
import by.senla.binancepriceservice.repository.PriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceServiceImplTest {

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private PriceSnapshotRepository repository;

    private PriceServiceImpl priceService;

    private final Instant from = Instant.parse("2026-10-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-10-31T00:00:00Z");

    @BeforeEach
    void setUp() {
        priceService = new PriceServiceImpl(binanceClient, repository, "BNBUSDT", Duration.ofSeconds(30));
    }

    @Test
    void fetchAndSaveCallBinanceAndSaveSnapshot() {
        when(binanceClient.fetchAvgPrice("BNBUSDT"))
                .thenReturn(new BinanceAvgPriceResponse(5, "600.12345678"));

        priceService.fetchAndSave();

        ArgumentCaptor<PriceSnapshot> captor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(repository).save(captor.capture());

        PriceSnapshot saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("BNBUSDT");
        assertThat(saved.getPrice()).isEqualByComparingTo("600.12345678");
        assertThat(saved.getFetchedAt()).isNotNull();
    }

    @Test
    void fetchAndSaveNotSaveWhenResponseIsNull() {
        when(binanceClient.fetchAvgPrice("BNBUSDT")).thenReturn(null);

        priceService.fetchAndSave();
        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveNotSaveWhenPriceIsNull() {
        when(binanceClient.fetchAvgPrice("BNBUSDT"))
                .thenReturn(new BinanceAvgPriceResponse(5, null));

        priceService.fetchAndSave();
        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveNotSaveOn429RateLimit() {
        when(binanceClient.fetchAvgPrice("BNBUSDT"))
                .thenThrow(mock(HttpClientErrorException.TooManyRequests.class));

        assertThatThrownBy(() -> priceService.fetchAndSave())
                .isInstanceOf(RateLimitException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveNotSaveOnOtherClientErrors() {
        RestClientResponseException exception = mock(RestClientResponseException.class);
        when(binanceClient.fetchAvgPrice("BNBUSDT")).thenThrow(exception);

        priceService.fetchAndSave();
        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveThrowOnUnexpectedErrors() {
        when(binanceClient.fetchAvgPrice("BNBUSDT"))
                .thenThrow(new RuntimeException("Unexpected"));

        assertThatThrownBy(() -> priceService.fetchAndSave())
                .isInstanceOf(RuntimeException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void getLatestReturnLatestSnapshot() {
        PriceSnapshot snapshot = PriceSnapshot.builder()
                .symbol("BNBUSDT")
                .price(new BigDecimal("600.50"))
                .fetchedAt(from)
                .build();
        when(repository.findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT"))
                .thenReturn(Optional.of(snapshot));

        PriceResponse result = priceService.getLatest();

        assertThat(result.symbol()).isEqualTo("BNBUSDT");
        assertThat(result.price()).isEqualByComparingTo("600.50");
        assertThat(result.fetchedAt()).isEqualTo(from);
    }

    @Test
    void getLatestThrowResourceNotFoundWhenNoData() {
        when(repository.findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.getLatest())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No data for symbol");
    }

    @Test
    void getAverageReturnScaledAverage() {
        when(repository.findAveragePrice("BNBUSDT", from, to))
                .thenReturn(new BigDecimal("600.123456789123"));

        AveragePriceResponse result = priceService.getAverage(from, to);

        assertThat(result.averagePrice()).isEqualByComparingTo("600.1234567891");
        assertThat(result.averagePrice().scale()).isEqualTo(10);
    }

    @Test
    void getAverageThrowWhenNoDataInRange() {
        when(repository.findAveragePrice("BNBUSDT", from, to)).thenReturn(null);

        assertThatThrownBy(() -> priceService.getAverage(from, to))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No data found in the specified range");
    }

    @Test
    void getAverageThrowWhenFromAfterTo() {
        assertThatThrownBy(() -> priceService.getAverage(to, from))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'from' must be before 'to'");
    }

    @Test
    void getAverageThrowWhenFromIsNull() {
        assertThatThrownBy(() -> priceService.getAverage(null, to))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPageReturnPagedResult() {
        PriceSnapshot s1 = PriceSnapshot
                .builder()
                .symbol("BNBUSDT")
                .price(new BigDecimal("600.10"))
                .fetchedAt(from).build();

        PriceSnapshot s2 = PriceSnapshot
                .builder()
                .symbol("BNBUSDT")
                .price(new BigDecimal("600.20"))
                .fetchedAt(from).build();

        Page<PriceSnapshot> page = new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 2), 10);
        when(repository.findBySymbolAndFetchedAtBetween(eq("BNBUSDT"), eq(from), eq(to), any()))
                .thenReturn(page);

        PagedPricesResponse result = priceService.getPage(from, to, 0, 2);

        assertThat(result.items()).hasSize(2);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void getPageThrowOnInvalidPagination() {
        assertThatThrownBy(() -> priceService.getPage(from, to, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> priceService.getPage(from, to, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> priceService.getPage(from, to, 0, 201))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTrendReturnUpward() {
        Instant mid = from.plusMillis((to.toEpochMilli() - from.toEpochMilli()) / 2);

        when(repository.findAveragePrice("BNBUSDT", from, mid))
                .thenReturn(new BigDecimal("100.0"));
        when(repository.findAveragePrice("BNBUSDT", mid, to))
                .thenReturn(new BigDecimal("110.0"));

        TrendResponse result = priceService.getTrend(from, to);

        assertThat(result.trend()).isEqualTo(Trend.UPWARD);
        assertThat(result.symbol()).isEqualTo("BNBUSDT");
        assertThat(result.startDate()).isEqualTo(from.atZone(java.time.ZoneOffset.UTC).toLocalDate());
    }

    @Test
    void getTrendReturnDownward() {
        Instant mid = from.plusMillis((to.toEpochMilli() - from.toEpochMilli()) / 2);

        when(repository.findAveragePrice("BNBUSDT", from, mid))
                .thenReturn(new BigDecimal("110.0"));
        when(repository.findAveragePrice("BNBUSDT", mid, to))
                .thenReturn(new BigDecimal("100.0"));

        TrendResponse result = priceService.getTrend(from, to);
        assertThat(result.trend()).isEqualTo(Trend.DOWNWARD);
    }

    @Test
    void getTrendReturnFlatWhenWithinThreshold() {
        Instant mid = from.plusMillis((to.toEpochMilli() - from.toEpochMilli()) / 2);

        when(repository.findAveragePrice("BNBUSDT", from, mid))
                .thenReturn(new BigDecimal("100.00"));
        when(repository.findAveragePrice("BNBUSDT", mid, to))
                .thenReturn(new BigDecimal("100.05"));

        TrendResponse result = priceService.getTrend(from, to);
        assertThat(result.trend()).isEqualTo(Trend.FLAT);
    }

    @Test
    void getTrendThrowWhenNotEnoughData() {
        Instant mid = from.plusMillis((to.toEpochMilli() - from.toEpochMilli()) / 2);

        when(repository.findAveragePrice("BNBUSDT", from, mid)).thenReturn(null);
        when(repository.findAveragePrice("BNBUSDT", mid, to)).thenReturn(null);

        assertThatThrownBy(() -> priceService.getTrend(from, to))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
