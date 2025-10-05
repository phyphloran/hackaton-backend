package hackaton.hackaton.Controllers;


import hackaton.hackaton.Dto.ExchangeRateDto;
import hackaton.hackaton.Services.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api")
public class ExchangeRateController {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @GetMapping("/getExchangeRates")
    public ResponseEntity<ExchangeRateDto> getExchangeRates() {
        return ResponseEntity.ok(exchangeRateService.getExchangeRates());
    }
}
