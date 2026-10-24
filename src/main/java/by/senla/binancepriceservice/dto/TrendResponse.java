package by.senla.binancepriceservice.dto;

import by.senla.binancepriceservice.dto.enums.Trend;

import java.time.LocalDate;

public record TrendResponse(
        String symbol,
        LocalDate start_date,
        LocalDate end_date,
        Trend trend
) {}
