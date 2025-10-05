package hackaton.hackaton.Services;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hackaton.hackaton.Dto.ExchangeRateDto;
import hackaton.hackaton.Private.ApiKeys;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class ExchangeRateService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    private volatile ExchangeRateDto cachedRates = new ExchangeRateDto();

    public ExchangeRateDto getExchangeRates() {
        return cachedRates;
    }

    private boolean processResponse(String response, ExchangeRateDto exchangeRateDto) {
        try {
            JsonNode json = mapper.readTree(response);

            if (json.has("result") && json.has("conversion_rate") && json.has("base_code")) {
                String result = json.get("result").asText();
                if (result.equalsIgnoreCase("success")) {
                    String conversionRate = json.get("conversion_rate").asText();
                    String baseCode = json.get("base_code").asText();
                    exchangeRateDto.getExchangeRates().put(baseCode, conversionRate);
                    return true;
                } else {
                    logger.warn("API вернуло ошибку для валюты: {}", json.get("base_code").asText());
                }
            } else {
                logger.warn("Неверная структура ответа API");
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке ответа API: {}", e.getMessage());
        }
        return false;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void updateExchangeRates() {
        String[] keys = { ApiKeys.RATEKEY1, ApiKeys.RATEKEY2 };
        String[] currencies = {"USD", "EUR", "CNY"};

        ExchangeRateDto exchangeRateDto = new ExchangeRateDto();

        for (String currency : currencies) {
            boolean success = false;
            for (String key : keys) {
                try {
                    String url = String.format("https://v6.exchangerate-api.com/v6/%s/pair/%s/RUB", key, currency);
                    String response = restTemplate.getForObject(url, String.class);

                    if (processResponse(response, exchangeRateDto)) {
                        success = true;
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Ошибка для {} с ключом {}: {}", currency, key, e.getMessage());
                }
            }

            if (!success) {
                logger.error("Не удалось обновить курс для {}", currency);
            }
        }

        cachedRates = exchangeRateDto;
        logger.info("Курсы успешно обновлены: {}", cachedRates.getExchangeRates());
    }

}
