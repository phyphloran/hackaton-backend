package hackaton.hackaton.Controllers;


import hackaton.hackaton.Dto.PostDto;
import hackaton.hackaton.Services.GnewsService;
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
public class GnewsController {

    @Autowired
    private GnewsService gnewsService;

    @GetMapping("/gNews")
    public ResponseEntity<?> getNews() {
        return ResponseEntity.ok(gnewsService.getCachedNews());
    }

    @GetMapping("/gNews/category/{category}")
    public ResponseEntity<?> getCategoryNews(@PathVariable String category) {
        Map<String, LinkedHashMap<String, PostDto>> categories = gnewsService.getCategorizedNews();

        if (!categories.containsKey(category)) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Категория '" + category + "' не найдена"));
        }

        return ResponseEntity.ok(categories.get(category));
    }

    @GetMapping("/gNews/getTopNews")
    public ResponseEntity<?> getTopNews() throws Exception {
        return ResponseEntity.ok(gnewsService.getSortedTopNews());
    }

}
