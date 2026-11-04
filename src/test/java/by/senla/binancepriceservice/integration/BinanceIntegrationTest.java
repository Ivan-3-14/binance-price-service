package by.senla.binancepriceservice.integration;

import by.senla.binancepriceservice.domain.PriceSnapshot;
import by.senla.binancepriceservice.exception.RateLimitException;
import by.senla.binancepriceservice.repository.PriceSnapshotRepository;
import by.senla.binancepriceservice.service.PriceService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BinanceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static WireMockServer wireMockServer = new WireMockServer(0);

    @Autowired
    private PriceService priceService;

    @Autowired
    private PriceSnapshotRepository repository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("binance.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("binance.poll-interval", () -> "PT24H");
        registry.add("binance.default-retry-after", () -> "1s");
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        wireMockServer.resetAll();
    }

    @Test
    void fetchAndSaveFetchFromBinanceAndSaveToDatabase() {
        stubFor(get(urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", equalTo("BNBUSDT"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {"mins":5,"price":"601.98765432"}
                    """)));

        priceService.fetchAndSave();

        Optional<PriceSnapshot> saved = repository.findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT");

        assertThat(saved).isPresent();
        assertThat(saved.get().getPrice()).isEqualByComparingTo("601.98765432");
        assertThat(saved.get().getSymbol()).isEqualTo("BNBUSDT");
        assertThat(saved.get().getFetchedAt()).isNotNull();

        verify(getRequestedFor(urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", equalTo("BNBUSDT")));
    }

    @Test
    void fetchAndSaveThrowRateLimitOn429() {
        stubFor(get(urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", equalTo("BNBUSDT"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")));

        assertThatThrownBy(() -> priceService.fetchAndSave())
                .isInstanceOf(RateLimitException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(60L);

        assertThat(repository.count()).isZero();
    }
}
