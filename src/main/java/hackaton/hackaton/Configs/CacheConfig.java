package hackaton.hackaton.Configs;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hackaton.hackaton.Dto.WeatherDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;


@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, String> cityByIpCache() {
        return Caffeine.newBuilder()
                .maximumSize(1600)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build();
    }

    @Bean
    public Cache<String, WeatherDto> weatherByCity() {
        return Caffeine.newBuilder()
                .maximumSize(1600)
                .expireAfterWrite(20, TimeUnit.MINUTES)
                .build();
    }

}
