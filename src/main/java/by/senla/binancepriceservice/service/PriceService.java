package by.senla.binancepriceservice.service;

import by.senla.binancepriceservice.dto.AveragePriceResponse;
import by.senla.binancepriceservice.dto.PagedPricesResponse;
import by.senla.binancepriceservice.dto.PriceResponse;
import by.senla.binancepriceservice.dto.TrendResponse;

import java.time.Instant;

public interface PriceService {

    void fetchAndSave();

    PriceResponse getLatest();

    AveragePriceResponse getAverage(Instant from, Instant to);

    PagedPricesResponse getPage(Instant from, Instant to, int page, int size);

    TrendResponse getTrend(Instant from, Instant to);


}
