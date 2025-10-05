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
public class GnewsService extends NewsService {

    private final RestTemplate restTemplate;

    private volatile LinkedHashMap<String, PostDto> sortedNews = new LinkedHashMap<>();

    private volatile Map<String, LinkedHashMap<String, PostDto>> categorizedNews = new HashMap<>();

    private volatile LinkedHashMap<String, PostDto> sortedTopNews = new LinkedHashMap<>();

    public GnewsService() {
        super();
        this.restTemplate = new RestTemplate();
    }

    Map<String, String> CATEGORIES = Map.of(
            "world", "world",
            "business", "business",
            "technology", "technology",
            "science", "science",
            "sports", "sports",
            "entertainment", "entertainment"
    );

    @Override
    public Map<String, LinkedHashMap<String, PostDto>> getCategorizedNews() {
        return Collections.unmodifiableMap(categorizedNews);
    }

    @Override
    protected LinkedHashMap<String, PostDto> getSortedNews() {
        return sortedNews;
    }

    public LinkedHashMap<String, PostDto> getSortedTopNews() {
        return sortedTopNews;
    }

    public void setSortedTopNews(LinkedHashMap<String, PostDto> sortedTopNews) {
        this.sortedTopNews = sortedTopNews;
    }

    @Override
    protected void setSortedNews(LinkedHashMap<String, PostDto> sortedNews) {
        this.sortedNews = sortedNews;
    }

    @Override
    public String getNews() throws Exception {
        String[] apiKeys = { ApiKeys.GNEWSKEY1, ApiKeys.GNEWSKEY2, ApiKeys.GNEWSKEY3, ApiKeys.GNEWSKEY4, ApiKeys.GNEWSKEY5, ApiKeys.GNEWSKEY6 };

        for (int i = 0; i < apiKeys.length; i++) {
            String url = "https://gnews.io/api/v4/search?q=а OR о OR в OR и OR т OR у&country=ru&max=30&apikey=" + apiKeys[i] + "&lang=ru";
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                } else if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи gNews не сработали. Последняя ошибка: " +
                            response.getStatusCode() + " - " + response.getBody());
                }
            } catch (Exception e) {
                if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи gNews не сработали", e);
                }
            }
        }
        throw new Exception("Не удалось получить данные ни с одним из API ключей gNews");
    }

    public String getTopNews() throws Exception {
        String[] apiKeys = { ApiKeys.GNEWSKEY1, ApiKeys.GNEWSKEY2, ApiKeys.GNEWSKEY3, ApiKeys.GNEWSKEY4, ApiKeys.GNEWSKEY5, ApiKeys.GNEWSKEY6 };

        for (int i = 0; i < apiKeys.length; i++) {
            String url = "https://gnews.io/api/v4/top-headlines?max=30&apikey=" + apiKeys[i] + "&lang=ru";
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                } else if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи gNews не сработали. Последняя ошибка: " +
                            response.getStatusCode() + " - " + response.getBody());
                }
            } catch (Exception e) {
                if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи gNews не сработали", e);
                }
            }
        }
        throw new Exception("Не удалось получить данные ни с одним из API ключей gNews");
    }

    @Override
    @Scheduled(fixedRate = 1_200_000)
    public void updateNews() {
        try {
            String response = getNews();
            Map<String, PostDto> freshNews = extractPostsData(response);

            Map<String, PostDto> allNews = new HashMap<>(sortedNews);
            allNews.putAll(freshNews);

            LinkedHashMap<String, PostDto> sorted = sortAndLimitNews(allNews, Const.POSTS_COUNT);
            setSortedNews(sorted);

            logger.info("GNews новости обновлены: {} новых, всего: {}",
                    freshNews.size(), sorted.size());

        } catch (RuntimeException e) {
            logger.warn("Проблема с обработкой GNews новостей: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Критическая ошибка обновления GNews: {}", e.getMessage(), e);
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

            if (jsonObject.has("articles") && jsonObject.get("articles").isJsonArray()) {
                JsonArray articles = jsonObject.getAsJsonArray("articles");
                int processed = 0;
                int skipped = 0;

                for (JsonElement elem : articles) {
                    if (!elem.isJsonObject()) {
                        skipped++;
                        continue;
                    }

                    JsonObject article = elem.getAsJsonObject();
                    String articleId = getSafeString(article, "id");
                    String title = getSafeString(article, "title");
                    String description = getSafeString(article, "description");
                    String date = getSafeString(article, "publishedAt");
                    String imageUrl = getSafeString(article, "image");
                    String link = getSafeString(article, "url");

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


            } else {
                logger.warn("Нет данных в ответе GNews API");
            }

        } catch (Exception e) {
            logger.error("Ошибка обработки данных GNews: {}", e.getMessage());
            throw new RuntimeException("Ошибка парсинга новостей GNews", e);
        }

        return posts;
    }

    @Override
    @Scheduled(fixedRate = 1_800_000)
    public void updateCategoryNews() {
        try {
            Thread.sleep(Const.SLEEP_THREAD_TIME);

            Map<String, LinkedHashMap<String, PostDto>> updatedCategorized = new HashMap<>(this.categorizedNews);

            for (Map.Entry<String, String> entry : CATEGORIES.entrySet()) {
                String categoryName = entry.getKey();
                String categoryParam = entry.getValue();

                boolean success = false;
                String[] apiKeys = { ApiKeys.GNEWSKEY1, ApiKeys.GNEWSKEY2, ApiKeys.GNEWSKEY3, ApiKeys.GNEWSKEY4, ApiKeys.GNEWSKEY5, ApiKeys.GNEWSKEY6 };

                for (int i = 0; i < apiKeys.length && !success; i++) {
                    String url = "https://gnews.io/api/v4/top-headlines?max=30&apikey=" + apiKeys[i] +
                            "&lang=ru&country=ru&topic=" + URLEncoder.encode(categoryParam, StandardCharsets.UTF_8);

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
                            logger.error("Ошибка загрузки категории gNews {}: {}", categoryName, e.getMessage());
                        }
                    }
                }
                Thread.sleep(Const.SLEEP_THREAD_TIME);
            }

            this.categorizedNews = updatedCategorized;
            logger.info("Новости gNews категорий ДОБАВЛЕНЫ: {}", categorizedNews.size());
        } catch (Exception e) {
            logger.error("Ошибка обновления категорий новостей gNews: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 1_300_000)
    public void updateTopNews() {
        try {
            Thread.sleep(Const.SLEEP_THREAD_TIME * 2);
            String response = getTopNews();
            Map<String, PostDto> freshTopNews = extractPostsData(response);

            Map<String, PostDto> allTopNews = new HashMap<>(sortedTopNews);
            allTopNews.putAll(freshTopNews);

            LinkedHashMap<String, PostDto> sorted = sortAndLimitNews(allTopNews, Const.POSTS_COUNT);
            setSortedTopNews(sorted);

            logger.info("Топ новости GNews обновлены: {} новых, всего: {}",
                    freshTopNews.size(), sorted.size());

        } catch (RuntimeException e) {
            logger.warn("Проблема с обработкой топ новостей GNews: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Критическая ошибка обновления топ новостей GNews: {}", e.getMessage(), e);
        }
    }
}