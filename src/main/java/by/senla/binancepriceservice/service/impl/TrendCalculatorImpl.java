package by.senla.binancepriceservice.service.impl;

import by.senla.binancepriceservice.dto.enums.Trend;
import by.senla.binancepriceservice.service.TrendCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TrendCalculatorImpl implements TrendCalculator {

    private final BigDecimal flatThreshold;

    public TrendCalculatorImpl(@Value("${binance.trend-flat-threshold:0.001}") BigDecimal flatThreshold) {
        this.flatThreshold = flatThreshold;
    }

    @Override
    public Trend calculate(BigDecimal first, BigDecimal second) {
        if (first.compareTo(BigDecimal.ZERO) == 0) {
            return Trend.FLAT;
        }
        BigDecimal diff = second.subtract(first);
        BigDecimal threshold = first.abs().multiply(flatThreshold);
        if (diff.abs().compareTo(threshold) <= 0) {
            return Trend.FLAT;
        }
        return diff.signum() > 0 ? Trend.UPWARD : Trend.DOWNWARD;
    }
}
