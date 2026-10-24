package by.senla.binancepriceservice.controller;

import by.senla.binancepriceservice.dto.AveragePriceResponse;
import by.senla.binancepriceservice.dto.PagedPricesResponse;
import by.senla.binancepriceservice.dto.PriceResponse;
import by.senla.binancepriceservice.dto.TrendResponse;
import by.senla.binancepriceservice.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/latest")
    public PriceResponse getLatest() {
        return priceService.getLatest();
    }

    @GetMapping("/average")
    public AveragePriceResponse getAverage(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return priceService.getAverage(from, to);
    }

    @GetMapping
    public PagedPricesResponse getPrices(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size) {
        return priceService.getPage(from, to, page, size);
    }

    @GetMapping("/trend")
    public TrendResponse getTrend(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return priceService.getTrend(from, to);
    }
}
