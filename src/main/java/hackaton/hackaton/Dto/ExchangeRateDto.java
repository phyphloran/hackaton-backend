package hackaton.hackaton.Dto;


import java.util.HashMap;
import java.util.Map;


public class ExchangeRateDto {

    Map<String, String> exchangeRates = new HashMap<>();

    public Map<String, String> getExchangeRates() {
        return exchangeRates;
    }

    public void setExchangeRates(Map<String, String> exchangeRates) {
        this.exchangeRates = exchangeRates;
    }
}
