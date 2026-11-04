package by.senla.binancepriceservice.service.impl;

import by.senla.binancepriceservice.dto.enums.Trend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrendCalculatorImplTest {

    private final TrendCalculatorImpl calculator =
            new TrendCalculatorImpl(new BigDecimal("0.001"));

    @Test
    void returnUpwardWhenSecondGreater() {
        assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("110")))
                .isEqualTo(Trend.UPWARD);
    }

    @Test
    void returnDownwardWhenSecondLower() {
        assertThat(calculator.calculate(new BigDecimal("110"), new BigDecimal("100")))
                .isEqualTo(Trend.DOWNWARD);
    }

    @Test
    void returnFlatWhenWithinThreshold() {
        assertThat(calculator.calculate(new BigDecimal("100.00"), new BigDecimal("100.05")))
                .isEqualTo(Trend.FLAT);
    }

    @Test
    void returnFlatWhenFirstIsZero() {
        assertThat(calculator.calculate(BigDecimal.ZERO, new BigDecimal("100")))
                .isEqualTo(Trend.FLAT);
    }

    @Test
    void returnUpwardWhenDiffExceedsThreshold() {
        assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("100.2")))
                .isEqualTo(Trend.UPWARD);
    }
}
