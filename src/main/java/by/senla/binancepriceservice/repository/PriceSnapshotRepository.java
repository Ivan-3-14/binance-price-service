package by.senla.binancepriceservice.repository;

import by.senla.binancepriceservice.domain.PriceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findFirstBySymbolOrderByFetchedAtDesc(String symbol);

    Page<PriceSnapshot> findBySymbolAndFetchedAtBetween(
            String symbol, Instant from, Instant to, Pageable pageable);

    @Query("select avg(p.price) from PriceSnapshot p " +
            "where p.symbol = :symbol and p.fetchedAt between :from and :to")
    BigDecimal findAveragePrice(@Param("symbol") String symbol,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
