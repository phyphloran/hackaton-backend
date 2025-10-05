package hackaton.hackaton.Configs;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(
                                "http://127.0.0.1:*",
                                "http://localhost:*",
                                "http://192.168.1.67:*",
                                "http://newshub.moscow",
                                "https://newshub.moscow",
                                "http://www.newshub.moscow",
                                "https://www.newshub.moscow"
                        )
                        .allowedMethods("GET")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}