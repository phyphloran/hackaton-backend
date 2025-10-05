package hackaton.hackaton.Controllers;


import hackaton.hackaton.Dto.PostDto;
import hackaton.hackaton.Services.NewsDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api")
public class NewsDataController {

    @Autowired
    private NewsDataService newsDataService;

    @GetMapping("/NewsData")
    public ResponseEntity<?> getNews() {
        return ResponseEntity.ok(newsDataService.getCachedNews());
    }

    @GetMapping("/NewsData/category/{category}")
    public ResponseEntity<?> getCategoryNews(@PathVariable String category) {
        Map<String, LinkedHashMap<String, PostDto>> categories = newsDataService.getCategorizedNews();

        if (!categories.containsKey(category)) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Категория '" + category + "' не найдена"));
        }
        return ResponseEntity.ok(categories.get(category));
    }

}
