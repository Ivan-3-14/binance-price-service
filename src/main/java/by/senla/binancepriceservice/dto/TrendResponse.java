package by.senla.binancepriceservice.dto;

import by.senla.binancepriceservice.dto.enums.Trend;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record TrendResponse(
        String symbol,

        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonProperty("end_date")
        LocalDate endDate,

        Trend trend
) {}
