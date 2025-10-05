package hackaton.hackaton.Services;


import com.google.gson.*;
import hackaton.hackaton.Const.Const;
import hackaton.hackaton.Dto.PostDto;
import hackaton.hackaton.Private.ApiKeys;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;


@Service
public class NewsApiService extends NewsService {

    private final RestTemplate restTemplate;

    private Integer searchCount = 0;

    private volatile Map<String, LinkedHashMap<String, PostDto>> categorizedNews = new HashMap<>();

    private volatile LinkedHashMap<String, PostDto> sortedTopNews = new LinkedHashMap<>();

    protected NewsApiService() {
        super();
        this.restTemplate = new RestTemplate();
    }

    Map<String, String> CATEGORIES = Map.of(
            "business", "business",
            "entertainment", "entertainment",
            "science", "science",
            "sports", "sports",
            "technology", "technology",
            "politics", "politics"
    );

    @Override
    protected LinkedHashMap<String, PostDto> getSortedNews() {
        return sortedTopNews;
    }

    @Override
    protected void setSortedNews(LinkedHashMap<String, PostDto> sortedNews) {
        this.sortedTopNews = sortedNews;
    }

    @Override
    public Map<String, LinkedHashMap<String, PostDto>> getCategorizedNews() {
        return Collections.unmodifiableMap(categorizedNews);
    }

    public LinkedHashMap<String, PostDto> getSortedTopNews() {
        return sortedTopNews;
    }

    public void setSortedTopNews(LinkedHashMap<String, PostDto> sortedTopNews) {
        this.sortedTopNews = sortedTopNews;
    }

    @Override
    public String getNews() throws Exception {
        return getTopNews();
    }

    public String getTopNews() throws Exception {
        String[] apiKeys = {ApiKeys.NEWSAPIKEY1, ApiKeys.NEWSAPIKEY2, ApiKeys.NEWSAPIKEY3, ApiKeys.NEWSAPIKEY4, ApiKeys.NEWSAPIKEY5, ApiKeys.NEWSAPIKEY6 };

        for (int i = 0; i < apiKeys.length; i++) {
            String url = "https://newsapi.org/v2/everything?q=а OR о OR в OR и OR т OR у&language=ru&sortBy=publishedAt&pageSize=100&apiKey=" + apiKeys[i];

            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                } else if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи NewsAPI не сработали. Последняя ошибка: " +
                            response.getStatusCode() + " - " + response.getBody());
                }
            } catch (Exception e) {
                if (i == apiKeys.length - 1) {
                    throw new Exception("Все API ключи NewsAPI не сработали", e);
                }
            }
        }
        throw new Exception("Не удалось получить данные ни с одним из API ключей NewsAPI");
    }

    @Override
    @Scheduled(fixedRate = 900_000)
    public void updateNews() {
        try {
            String response = getTopNews();
            Map<String, PostDto> freshTopNews = extractPostsData(response);

            Map<String, PostDto> allTopNews = new HashMap<>(sortedTopNews);
            allTopNews.putAll(freshTopNews);

            LinkedHashMap<String, PostDto> sorted = sortAndLimitNews(allTopNews, Const.POSTS_COUNT);

            setSortedTopNews(sorted);
            logger.info("newsApi новости обновлены: {} новых, всего: {}",
                    freshTopNews.size(), sorted.size());
        } catch (RuntimeException e) {
            logger.warn("Проблема с обработкой newsApi новостей: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Критическая ошибка обновления newsApi: {}", e.getMessage(), e);
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

            if (!jsonObject.has("status") || !"ok".equals(getSafeString(jsonObject, "status"))) {
                String errorMessage = getSafeString(jsonObject, "message");
                throw new RuntimeException("API ошибка: " +
                        (errorMessage != null ? errorMessage : "Unknown error"));
            }

            if (!jsonObject.has("articles") || !jsonObject.get("articles").isJsonArray()) {
                logger.warn("Нет данных в ответе API");
                return posts;
            }

            JsonArray articles = jsonObject.getAsJsonArray("articles");
            int processed = 0;
            int skipped = 0;

            Set<String> seenTitles = new HashSet<>();

            for (JsonElement resultElement : articles) {
                if (!resultElement.isJsonObject()) {
                    skipped++;
                    continue;
                }

                JsonObject article = resultElement.getAsJsonObject();
                String title = getSafeString(article, "title");
                String description = getSafeString(article, "description");
                String date = getSafeString(article, "publishedAt");
                String link = getSafeString(article, "url");

                if (isBlank(title) || isBlank(description) || isBlank(date) || isBlank(link)) {
                    skipped++;
                    continue;
                }

                if (isDuplicateTitle(seenTitles, title)) {
                    skipped++;
                    continue;
                }

                description = removeReadMorePhrases(description);
                description = truncateDescription(description, 300);

                if (!isMostlyRussian(description, 40.0)) {
                    skipped++;
                    continue;
                }

                String articleId = generateContentId(title, link, date);

                if (posts.containsKey(articleId)) {
                    skipped++;
                    continue;
                }

                String imageUrl = getSafeString(article, "urlToImage");

                PostDto postDto = createPostDto(articleId, title, description, date, imageUrl, link);
                posts.put(articleId, postDto);
                processed++;
            }

            return posts;

        } catch (Exception e) {
            logger.error("Ошибка обработки данных: {}", e.getMessage());
            throw new RuntimeException("Ошибка парсинга новостей", e);
        }
    }

    private boolean isDuplicateTitle(Set<String> seenTitles, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalized = title.trim().toLowerCase();
        if (seenTitles.contains(normalized)) {
            return true;
        }
        seenTitles.add(normalized);
        return false;
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
                String[] apiKeys = {ApiKeys.NEWSAPIKEY1, ApiKeys.NEWSAPIKEY2, ApiKeys.NEWSAPIKEY3, ApiKeys.NEWSAPIKEY4, ApiKeys.NEWSAPIKEY5, ApiKeys.NEWSAPIKEY6 };

                for (int i = 0; i < apiKeys.length && !success; i++) {
                    String url = "https://newsapi.org/v2/everything?q=" + categoryParam +
                            "&language=ru&sortBy=publishedAt&pageSize=50&apiKey=" + apiKeys[i];


                    try {
                        ResponseEntity<String> catResponse = restTemplate.getForEntity(url, String.class);
                        if (catResponse.getStatusCode().is2xxSuccessful()) {
                            Map<String, PostDto> freshCatNews = extractPostsData(catResponse.getBody());

                            if (freshCatNews.isEmpty()) {
                                String fallbackUrl = "https://newsapi.org/v2/top-headlines?category=" + categoryParam +
                                        "&country=ru&pageSize=50&apiKey=" + apiKeys[i];
                                logger.info("Для категории '{}' language=ru пусто, пробуем country=ru", categoryName);

                                catResponse = restTemplate.getForEntity(fallbackUrl, String.class);
                                if (catResponse.getStatusCode().is2xxSuccessful()) {
                                    freshCatNews = extractPostsData(catResponse.getBody());
                                }
                            }

                            LinkedHashMap<String, PostDto> existingCatNews =
                                    updatedCategorized.getOrDefault(categoryName, new LinkedHashMap<>());

                            Map<String, PostDto> mergedCatNews = new HashMap<>(existingCatNews);
                            mergedCatNews.putAll(freshCatNews);

                            LinkedHashMap<String, PostDto> sortedCatNews =
                                    sortAndLimitNews(mergedCatNews, Const.POSTS_COUNT * 2);

                            updatedCategorized.put(categoryName, sortedCatNews);
                            success = true;

                            logger.info("Категория '{}' обновлена: {} новых новостей",
                                    categoryName, freshCatNews.size());

                        } else {
                            logger.warn("Ошибка для категории {}: {}", categoryName, catResponse.getStatusCode());
                        }
                    } catch (Exception e) {
                        if (i == apiKeys.length - 1) {
                            logger.error("Ошибка загрузки категории NewsApi {}: {}", categoryName, e.getMessage());
                        }
                    }
                }
                Thread.sleep(Const.SLEEP_THREAD_TIME);
            }

            this.categorizedNews = updatedCategorized;
            logger.info("Новости NewsApi категорий обновлены. Всего категорий: {}", categorizedNews.size());

        } catch (Exception e) {
            logger.error("Ошибка обновления категорий новостей NewsApi: {}", e.getMessage());
        }
    }


    private String generateContentId(String title, String link, String date) {
        try {
            String content = (title + link + date).toLowerCase().trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "newsapi_" + hexString.toString();
        } catch (Exception e) {
            return "newsapi_" + Integer.toHexString((title + link + date).hashCode());
        }
    }

    public String searchNews(String[] keywords) throws Exception {
        if (keywords == null || keywords.length == 0) {
            throw new IllegalArgumentException("Массив keywords не должен быть пустым");
        }

        if (searchCount >= Const.SEARCH_LIMI_PER_DAY) {
            throw new UnsupportedOperationException("Количество запросов на сегодняшний день превышено. Лимит: " + Const.SEARCH_LIMI_PER_DAY.toString() + " запросов/день");
        }

        String query = String.join(" OR ", keywords);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newHttpClient();
        String[] apiKeys = {ApiKeys.NEWSAPIKEY1, ApiKeys.NEWSAPIKEY2, ApiKeys.NEWSAPIKEY3, ApiKeys.NEWSAPIKEY4, ApiKeys.NEWSAPIKEY5, ApiKeys.NEWSAPIKEY6 };

        for (int i = 0; i < apiKeys.length; i++) {
            String url = "https://newsapi.org/v2/everything?q=" + encodedQuery +
                    "&language=ru&apiKey=" + apiKeys[i] + "&pageSize=100";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                searchCount++;
                System.out.println(searchCount);
                return response.body();
            } else if (i == apiKeys.length - 1) {
                throw new Exception("Все API ключи NewsAPI не сработали. Последняя ошибка: " +
                        response.statusCode() + " - " + response.body());
            }
        }

        throw new Exception("Не удалось получить данные ни с одним из API ключей NewsAPI");
    }

    public LinkedHashMap<String, PostDto> searchNewsSorted(String[] keywords) throws Exception {
        String jsonString = searchNews(keywords);
        Map<String, PostDto> posts = extractPostsData(jsonString);
        return sortAndLimitNews(posts, Const.POSTS_COUNT);
    }


    @Scheduled(cron = "0 0 23 * * ?")
    public void updateSearchLimit() {
        searchCount = 0;
        logger.info("Лимит для поиска newsApi обновлён");
    }
}