package hackaton.hackaton.Services;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import hackaton.hackaton.Dto.WeatherDto;
import hackaton.hackaton.Private.ApiKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;


@Service
public class WeatherService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<String, String> cityByIpCache;

    private final Cache<String, WeatherDto> weatherByCity;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public WeatherService(Cache<String, String> cityByIpCache, Cache<String, WeatherDto> weatherByCity) {
        this.cityByIpCache = cityByIpCache;
        this.weatherByCity = weatherByCity;
    }

    public WeatherDto getWeather(HttpServletRequest request) {
        String city = getCityByIp(request);

        String[] apiKeys = { ApiKeys.WEATHERAPIKEY1, ApiKeys.WEATHERAPIKEY2 };

        WeatherDto cachedWeatherDto = weatherByCity.getIfPresent(city);
        if (cachedWeatherDto != null) {
            logger.info("Погода взята из кеша для города: {}", city);
            return cachedWeatherDto;
        }

        WeatherDto weatherDto = new WeatherDto();

        for (int i = 0; i < apiKeys.length; i++) {
            try {
                String url = "http://api.weatherapi.com/v1/current.json?key=" + apiKeys[i] + "&q=" + city + "&lang=ru";
                String response = restTemplate.getForObject(url, String.class);

                JsonNode json = mapper.readTree(response);
                if (json.has("location") && json.has("current")) {
                    JsonNode location = json.get("location");
                    JsonNode current = json.get("current");
                    JsonNode condition = current.get("condition");

                    String cityName = location.get("name").asText();
                    BigDecimal temp = new BigDecimal(current.get("temp_c").asText());
                    String weatherCondition = condition.get("text").asText();

                    weatherDto.setCity(cityName);
                    weatherDto.setTemperature(temp);
                    weatherDto.setCondition(weatherCondition);

                    weatherByCity.put(cityName, weatherDto);

                    logger.info("Погода успешно получена с API для города: {} с ключом #{}", cityName, (i + 1));
                    return weatherDto;
                }
            } catch (Exception e) {
                logger.error("Ключ #{} не сработал для города {}: {}", (i + 1), city, e.getMessage());

                if (i == apiKeys.length - 1) {
                    logger.error("Все API ключи погоды не сработали для города: {}", city);
                    throw new RuntimeException("Все API ключи погоды не сработали для города: " + city, e);
                }
            }
        }
        throw new RuntimeException("Не удалось получить погоду для города: " + city);
    }

    public String getCityByIp(HttpServletRequest request) {
        String userIp = request.getRemoteAddr();
        String[] apiKeys = { ApiKeys.CITYKEY1, ApiKeys.CITYKEY2 };

        if ("127.0.0.1".equals(userIp) || "0:0:0:0:0:0:0:1".equals(userIp)) {
            userIp = "8.8.8.8";
            logger.info("Локальный IP заменен на: {}", userIp);
        }

        String cachedCity = cityByIpCache.getIfPresent(userIp);
        if (cachedCity != null) {
            logger.info("Город из кеша: {} -> {}", userIp, cachedCity);
            return cachedCity;
        }

        for (int i = 0; i < apiKeys.length; i++) {
            try {
                String url = "https://api.2ip.io/" + userIp + "?token=" + apiKeys[i];
                logger.info("Запрос города для IP {} с ключом #{}/{}", userIp, i + 1, apiKeys.length);

                String response = restTemplate.getForObject(url, String.class);
                JsonNode json = mapper.readTree(response);

                if (json.has("city") && !json.get("city").isNull()) {
                    String city = json.get("city").asText();
                    cityByIpCache.put(userIp, city);
                    logger.info("Определен город: {} -> {} (ключ #{})", userIp, city, i + 1);
                    return city;
                } else {
                    logger.warn("Ключ #{} вернул невалидный ответ для IP {}", i + 1, userIp);
                }

            } catch (Exception e) {
                logger.error("Ошибка с ключом #{} для IP {}: {}", i + 1, userIp, e.getMessage());

                if (i == apiKeys.length - 1) {
                    logger.error("Все {} ключа(ов) не сработали для IP: {}", apiKeys.length, userIp);
                }
            }
        }

        return "Moscow";
    }
}
