package hackaton.hackaton.Services;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hackaton.hackaton.Dto.PostDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public abstract class NewsService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected NewsService() {}

    protected abstract void updateCategoryNews();

    protected abstract LinkedHashMap<String, PostDto> getSortedNews();

    protected abstract void setSortedNews(LinkedHashMap<String, PostDto> sortedNews);

    public abstract String getNews() throws Exception;

    public abstract Map<String, PostDto> extractPostsData(String jsonResponse);

    public abstract void updateNews();

    public abstract Map<String, LinkedHashMap<String, PostDto>> getCategorizedNews();


    protected String fetchFromApi(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Ошибка при запросе API NewsData: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }

    protected LocalDateTime parseDate(String date) {
        if (date == null || date.isBlank()) return LocalDateTime.MIN;
        try {
            return OffsetDateTime.parse(date).toLocalDateTime();
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(date, formatter);
            } catch (Exception ex) {
                return LocalDateTime.MIN;
            }
        }
    }

    protected boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    protected String getSafeString(JsonObject jsonObject, String key) {
        if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
            return jsonObject.get(key).getAsString();
        }
        return "";
    }

    protected boolean isMostlyRussian(String text, double thresholdPercent) {
        if (text == null || text.isBlank()) return false;

        int russianChars = 0;
        int totalLetters = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalLetters++;
                if ((c >= 'А' && c <= 'я') || c == 'ё' || c == 'Ё') russianChars++;
            }
        }

        if (totalLetters == 0) return false;
        return (double) russianChars / totalLetters * 100 >= thresholdPercent;
    }

    protected String truncateDescription(String description, int maxLength) {
        if (description == null || description.isBlank()) return "";

        String sub = description.length() > maxLength ? description.substring(0, maxLength) : description;

        int ellipsisIndex = sub.indexOf("…");
        if (ellipsisIndex == -1) ellipsisIndex = sub.indexOf("...");

        if (ellipsisIndex != -1) {
            int lastDot = sub.lastIndexOf('.', ellipsisIndex);
            int lastExclam = sub.lastIndexOf('!', ellipsisIndex);
            int lastQuestion = sub.lastIndexOf('?', ellipsisIndex);

            int cutoff = Math.max(Math.max(lastDot, lastExclam), lastQuestion);

            if (cutoff != -1) {
                sub = sub.substring(0, cutoff + 1).trim();
            }
            else {
                sub = sub.substring(0, ellipsisIndex + (sub.startsWith("...") ? 3 : 1)).trim();
            }
        } else {
            int lastDot = sub.lastIndexOf('.');
            int lastExclam = sub.lastIndexOf('!');
            int lastQuestion = sub.lastIndexOf('?');

            int cutoff = Math.max(Math.max(lastDot, lastExclam), lastQuestion);

            if (cutoff != -1) {
                sub = sub.substring(0, cutoff + 1).trim();
            }
        }

        return sub;
    }

    protected String removeReadMorePhrases(String description) {
        if (description == null || description.isBlank()) return "";
        String[] phrases = {"Читать далее", "читать далее", "читать дальше", "подробнее", "подробней"};
        for (String phrase : phrases) description = description.replaceAll("(?i)" + phrase, "").trim();
        return description;
    }

    protected boolean isEmptyResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            logger.warn("Пустой ответ от API");
            return true;
        }
        return false;
    }

    protected JsonObject parseJsonObject(String jsonResponse) {
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonResponse);
            if (!jsonElement.isJsonObject()) {
                throw new RuntimeException("Некорректный JSON формат - ожидался объект");
            }
            return jsonElement.getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при парсинге JSON: " + e.getMessage(), e);
        }
    }

    public LinkedHashMap<String, PostDto> getCachedNews() {
        return new LinkedHashMap<>(getSortedNews());
    }

    protected LinkedHashMap<String, PostDto> sortAndLimitNews(Map<String, PostDto> news, Integer limit) {
        List<PostDto> newsList = new ArrayList<>(news.values());
        newsList.sort((n1, n2) -> parseDate(n2.getDate()).compareTo(parseDate(n1.getDate())));

        LinkedHashMap<String, PostDto> sorted = new LinkedHashMap<>();
        int count = Math.min(limit, newsList.size());
        for (int i = 0; i < count; i++) {
            PostDto newsItem = newsList.get(i);
            sorted.put(newsItem.getId(), newsItem);
        }
        return sorted;
    }

    protected PostDto createPostDto(String id, String title, String description,
                                  String date, String imageUrl, String link) {
        PostDto postDto = new PostDto();
        postDto.setId(id);
        postDto.setTitle(title);
        postDto.setDescription(description);
        postDto.setDate(date);
        postDto.setImage_url(imageUrl);
        postDto.setLink(link);
        return postDto;
    }
}