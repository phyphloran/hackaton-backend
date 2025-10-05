package hackaton.hackaton.Dto;


import java.math.BigDecimal;


public class WeatherDto {

    private BigDecimal temperature;

    private String city;

    private String condition;

    public WeatherDto() {
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
