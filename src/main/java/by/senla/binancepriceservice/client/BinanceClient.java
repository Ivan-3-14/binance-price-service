package by.senla.binancepriceservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceClient {

    private final RestClient binanceRestClient;

    public BinanceAvgPriceResponse fetchAvgPrice(String symbol) {
        log.debug("Fetching avg price for {}", symbol);
        return binanceRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/avgPrice")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(BinanceAvgPriceResponse.class);
    }
}
