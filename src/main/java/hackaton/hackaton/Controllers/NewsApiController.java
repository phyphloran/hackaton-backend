package hackaton.hackaton.Controllers;


import hackaton.hackaton.Dto.PostDto;
import hackaton.hackaton.Services.NewsApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api")
public class NewsApiController {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private NewsApiService newsApiService;

    @GetMapping("/NewsApi")
    public ResponseEntity<?> getNews() {
        return ResponseEntity.ok(newsApiService.getCachedNews());
    }

    @GetMapping("/NewsApi/category/{category}")
    public ResponseEntity<?> getCategoryNews(@PathVariable String category) {
        Map<String, LinkedHashMap<String, PostDto>> categories = newsApiService.getCategorizedNews();

        if (!categories.containsKey(category)) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Категория '" + category + "' не найдена"));
        }

        return ResponseEntity.ok(categories.get(category));
    }

    @GetMapping("/NewsApi/search")
    public ResponseEntity<?> searchNews(@RequestParam(required = false) String[] keywords) {
        try {
            LinkedHashMap<String, PostDto> sortedPosts = newsApiService.searchNewsSorted(keywords);
            return ResponseEntity.ok(sortedPosts);
        } catch (UnsupportedOperationException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(429).build();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

}