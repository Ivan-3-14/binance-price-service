package by.senla.binancepriceservice.service;

import by.senla.binancepriceservice.dto.enums.Trend;

import java.math.BigDecimal;

public interface TrendCalculator {
    Trend calculate(BigDecimal firstHalf, BigDecimal secondHalf);
}
