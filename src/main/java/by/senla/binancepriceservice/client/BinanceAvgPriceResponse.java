package by.senla.binancepriceservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Binance GET /api/v3/avgPrice response:
 * The price is returned as a string - we parse it into a `BigDecimal` manually to avoid any loss of precision
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAvgPriceResponse(

        @JsonProperty("mins")
        Integer mins,

        @JsonProperty("price")
        String price,

        @JsonProperty("closeTime")
        Long closeTime
) {
}
