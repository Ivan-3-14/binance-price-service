package by.senla.binancepriceservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import by.senla.binancepriceservice.repository.PriceSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BinancePriceServiceApplicationTests {

    @Autowired
    private PriceSnapshotRepository repository;

    @Test
    void contextLoadsAndDatabaseIsAvailable() {
        long count = repository.count();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
