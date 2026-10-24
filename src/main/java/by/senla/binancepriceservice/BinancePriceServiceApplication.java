package by.senla.binancepriceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BinancePriceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BinancePriceServiceApplication.class, args);
    }
}
