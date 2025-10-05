package hackaton.hackaton.Services;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hackaton.hackaton.Const.Const;
import hackaton.hackaton.Dto.PostDto;
import hackaton.hackaton.Private.ApiKeys;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Service
public class NewsDataService extends NewsService {

    private final RestTemplate restTemplate;

    private volatile LinkedHashMap<String, PostDto> sortedNews = new LinkedHashMap<>();

    private volatile Map<String, LinkedHashMap<String, PostDto>> categorizedNews = new HashMap<>();

    public NewsDataService() {
        super();
        this.restTemplate = new RestTemplate();
    }

    Map<String, String> CATEGORIES = Map.of(
            "politics", "politics",
            "business", "business",
            "technology", "technology",
            "science", "science",
            "sports", "sports",
            "entertainment", "entertainment"
    );

    @Override
    protected LinkedHashMap<String, PostDto> getSortedNews() {
        return sortedNews;
    }

    @Override
    protected void setSortedNews(LinkedHashMap<String, PostDto> sortedNews) {
        this.sortedNews = sortedNews;
    }

    @Override
    public Map<String, LinkedHashMap<String, PostDto>> getCategorizedNews() {
        return Collections.unmodifiableMap(categorizedNews);
    }

    @Override
    public String getNews() throws Exception {
        String[] apiKeys = { ApiKeys.NEWSDATAAPIKEY1, ApiKeys.NEWSDATAAPIKEY2, ApiKeys.NEWSDATAAPIKEY3, ApiKeys.NEWSDATAAPIKEY4 };

        for (int i = 0; i < apiKeys.length; i++) {
            String url = "https://newsdata.io/api/1/news?apikey=" + apiKeys[i] + "&language=ru&size=10";
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                } else if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи NewsData не сработали. Последняя ошибка: " +
                            response.getStatusCode() + " - " + response.getBody());
                }
            } catch (Exception e) {
                if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи NewsData не сработали", e);
                }
            }
        }
        throw new Exception("Не удалось получить данные ни с одним из API ключей NewsData");
    }

    @Override
    @Scheduled(fixedRate = 700_000)
    public void updateNews() {
        try {
            String response = getNews();
            Map<String, PostDto> freshNews = extractPostsData(response);

            Map<String, PostDto> allNews = new HashMap<>(sortedNews);
            allNews.putAll(freshNews);

            LinkedHashMap<String, PostDto> sorted = sortAndLimitNews(allNews, Const.POSTS_COUNT);
            setSortedNews(sorted);

            logger.info("NewsData новости обновлены: {} новых, всего: {}",
                    freshNews.size(), sorted.size());

        } catch (RuntimeException e) {
            logger.warn("Проблема с обработкой NewsData новостей: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Критическая ошибка обновления NewsData: {}", e.getMessage(), e);
        }
    }

    @Override
    public Map<String, PostDto> extractPostsData(String jsonResponse) {
        Map<String, PostDto> posts = new HashMap<>();

        try {
            if (isEmptyResponse(jsonResponse)) {
                return posts;
            }

            JsonObject jsonObject = parseJsonObject(jsonResponse);

            if (!jsonObject.has("status") || !"success".equals(getSafeString(jsonObject, "status"))) {
                String errorMessage = getSafeString(jsonObject, "message");
                throw new RuntimeException("API ошибка: " +
                        (errorMessage != null ? errorMessage : "Unknown error"));
            }

            if (!jsonObject.has("results") || !jsonObject.get("results").isJsonArray()) {
                logger.warn("Нет данных в ответе API");
                return posts;
            }

            JsonArray results = jsonObject.getAsJsonArray("results");
            int processed = 0;
            int skipped = 0;

            for (JsonElement resultElement : results) {
                if (!resultElement.isJsonObject()) {
                    skipped++;
                    continue;
                }

                JsonObject article = resultElement.getAsJsonObject();
                String articleId = getSafeString(article, "article_id");
                String title = getSafeString(article, "title");
                String description = getSafeString(article, "description");
                String date = getSafeString(article, "pubDate");
                String imageUrl = getSafeString(article, "image_url");
                String link = getSafeString(article, "link");

                if (isBlank(articleId) || isBlank(title) || isBlank(description)
                        || isBlank(date) || isBlank(imageUrl) || isBlank(link)) {

                    skipped++;
                    continue;
                }

                description = removeReadMorePhrases(description);
                description = truncateDescription(description, 300);

                if (!isMostlyRussian(description, 40.0)) {

                    skipped++;
                    continue;
                }

                PostDto postDto = createPostDto(articleId, title, description, date, imageUrl, link);

                posts.put(articleId, postDto);
                processed++;
            }

            return posts;

        } catch (Exception e) {
            logger.error("Ошибка обработки данных NewsData: {}", e.getMessage());
            throw new RuntimeException("Ошибка парсинга новостей NewsData", e);
        }
    }

    @Override
    @Scheduled(fixedRate = 900_000)
    public void updateCategoryNews() {
        try {
            Thread.sleep(Const.SLEEP_THREAD_TIME);

            Map<String, LinkedHashMap<String, PostDto>> updatedCategorized = new HashMap<>(this.categorizedNews);

            for (Map.Entry<String, String> entry : CATEGORIES.entrySet()) {
                String categoryName = entry.getKey();
                String categoryParam = entry.getValue();

                boolean success = false;
                String[] apiKeys = { ApiKeys.NEWSDATAAPIKEY1, ApiKeys.NEWSDATAAPIKEY2, ApiKeys.NEWSDATAAPIKEY3, ApiKeys.NEWSDATAAPIKEY4 };

                for (int i = 0; i < apiKeys.length && !success; i++) {
                    String url = "https://newsdata.io/api/1/news?apikey=" + apiKeys[i] +
                            "&category=" + URLEncoder.encode(categoryParam, StandardCharsets.UTF_8) +
                            "&country=ru&language=ru&size=10";

                    try {
                        ResponseEntity<String> catResponse = restTemplate.getForEntity(url, String.class);
                        if (catResponse.getStatusCode().is2xxSuccessful()) {
                            Map<String, PostDto> freshCatNews = extractPostsData(catResponse.getBody());

                            LinkedHashMap<String, PostDto> existingCatNews =
                                    updatedCategorized.getOrDefault(categoryName, new LinkedHashMap<>());

                            Map<String, PostDto> mergedCatNews = new HashMap<>(existingCatNews);
                            mergedCatNews.putAll(freshCatNews);

                            LinkedHashMap<String, PostDto> sortedCatNews =
                                    sortAndLimitNews(mergedCatNews, Const.POSTS_COUNT);

                            updatedCategorized.put(categoryName, sortedCatNews);
                            success = true;
                        }
                    } catch (Exception e) {
                        if (i == apiKeys.length - 1) {
                            logger.error("Ошибка загрузки категории NewsData {}: {}", categoryName, e.getMessage());
                        }
                    }
                }
                Thread.sleep(Const.SLEEP_THREAD_TIME);
            }

            this.categorizedNews = updatedCategorized;
            logger.info("Новости NewsData категорий ДОБАВЛЕНЫ: {}", categorizedNews.size());
        } catch (Exception e) {
            logger.error("Ошибка обновления категорий новостей NewsData: {}", e.getMessage());
        }
    }
}