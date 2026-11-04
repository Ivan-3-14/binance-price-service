package by.senla.binancepriceservice.integration;

import by.senla.binancepriceservice.domain.PriceSnapshot;
import by.senla.binancepriceservice.repository.PriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import by.senla.binancepriceservice.TestcontainersConfiguration;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "binance.base-url=http://localhost:1")
@Import(TestcontainersConfiguration.class)
class PriceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PriceSnapshotRepository repository;

    private final Instant from = Instant.parse("2026-10-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-10-31T23:59:59Z");

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void getLatestReturn200AndJson() throws Exception {
        repository.save(PriceSnapshot.builder()
                .symbol("BNBUSDT")
                .price(new BigDecimal("600.50"))
                .fetchedAt(Instant.parse("2026-10-15T12:00:00Z"))
                .build());

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.price").value(closeTo(600.5, 0.001)))
                .andExpect(jsonPath("$.fetchedAt").value("2026-10-15T12:00:00Z"));
    }

    @Test
    void getLatestReturn404WhenNoData() throws Exception {
        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No data for symbol BNBUSDT"));
    }

    @Test
    void getAverageReturn200AndAveragePrice() throws Exception {
        repository.saveAll(List.of(
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("100.00")).fetchedAt(from).build(),
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("200.00")).fetchedAt(from.plusSeconds(3600)).build()
        ));

        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice").value(closeTo(150.0, 0.001)));
    }

    @Test
    void getAverageReturn400WhenFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", to.toString())
                        .param("to", from.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must be before 'to'"));
    }

    @Test
    void getAverageReturn400WhenFromMissing() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average")
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Required parameter 'from'")));
    }

    @Test
    void getPricesReturn200AndPagedResult() throws Exception {
        repository.saveAll(List.of(
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("100.00")).fetchedAt(from).build(),
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("200.00")).fetchedAt(from.plusSeconds(3600)).build()
        ));

        mockMvc.perform(get("/api/v1/prices")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getTrendReturn200AndTrend() throws Exception {
        repository.saveAll(List.of(
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("100.00")).fetchedAt(from).build(),
                PriceSnapshot.builder().symbol("BNBUSDT").price(new BigDecimal("200.00")).fetchedAt(to).build()
        ));

        mockMvc.perform(get("/api/v1/prices/trend")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.start_date").value("2026-10-01"))
                .andExpect(jsonPath("$.end_date").value("2026-10-31"))
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }

    @Test
    void getTrendReturn404WhenNotEnoughData() throws Exception {
        mockMvc.perform(get("/api/v1/prices/trend")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isNotFound());
    }
}
