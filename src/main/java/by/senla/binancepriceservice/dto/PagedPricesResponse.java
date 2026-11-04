package by.senla.binancepriceservice.dto;

import java.util.List;

public record PagedPricesResponse(
        List<PriceResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {}
