package by.senla.binancepriceservice.service;

import by.senla.binancepriceservice.domain.PriceSnapshot;
import by.senla.binancepriceservice.dto.AveragePriceResponse;
import by.senla.binancepriceservice.dto.PagedPricesResponse;
import by.senla.binancepriceservice.dto.PriceResponse;
import by.senla.binancepriceservice.dto.TrendResponse;

import java.time.Instant;
import java.util.Optional;

public interface PriceService {

    Optional<PriceSnapshot> fetchAndSave();

    PriceResponse getLatest();

    AveragePriceResponse getAverage(Instant from, Instant to);

    PagedPricesResponse getPage(Instant from, Instant to, int page, int size);

    TrendResponse getTrend(Instant from, Instant to);


}
