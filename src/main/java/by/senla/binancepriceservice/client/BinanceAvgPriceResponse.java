package by.senla.binancepriceservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAvgPriceResponse(
        Integer mins,
        String price
) {
}
