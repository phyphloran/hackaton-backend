package hackaton.hackaton.Controllers;


import hackaton.hackaton.Dto.WeatherDto;
import hackaton.hackaton.Services.WeatherService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/getWeather")
    public ResponseEntity<WeatherDto> getWeather(HttpServletRequest request) {
        WeatherDto weather = weatherService.getWeather(request);
        return ResponseEntity.ok(weather);
    }

}
