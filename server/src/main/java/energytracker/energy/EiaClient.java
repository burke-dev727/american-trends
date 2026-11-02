package energytracker.energy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Pages through the EIA Open Data API for monthly electric-power generation. */
public final class EiaClient {

    static final String URL = "https://api.eia.gov/v2/electricity/electric-power-operational-data/data/";
    private static final int PAGE_LENGTH = 5000;

    private final String apiKey;
    private final HttpClient http;

    public EiaClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    EiaClient(String apiKey, HttpClient http) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.http = http;
    }

    public List<EiaRecord> fetch(String start, String end) {
        List<EiaRecord> records = new ArrayList<>();
        for (int offset = 0; ; offset += PAGE_LENGTH) {
            Page page = getPage(start, end, offset);
            if (page.response != null && page.response.data != null) {
                records.addAll(page.response.data);
            }
            int total = parseTotal(page);
            int pageSize = page.response == null || page.response.data == null ? 0 : page.response.data.size();
            if (records.size() >= total || pageSize == 0) {
                return records;
            }
        }
    }

    private int parseTotal(Page page) {
        String raw = page.response == null ? null : page.response.total;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new EnergyDataException("parsing EIA total \"" + raw + "\"", ex);
        }
    }

    private Page getPage(String start, String end, int offset) {
        String query = form(
                "api_key", apiKey,
                "frequency", "monthly",
                "data[]", "generation",
                "facets[location][]", "US",
                "facets[sectorid][]", "98",
                "start", start,
                "end", end,
                "length", Integer.toString(PAGE_LENGTH),
                "offset", Integer.toString(offset));
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EnergyDataException("EIA request failed: " + sanitize(ex));
        } catch (IOException ex) {
            throw new EnergyDataException("EIA request failed: " + sanitize(ex));
        }
        if (response.statusCode() != 200) {
            throw new EnergyDataException("EIA API returned " + response.statusCode());
        }
        try {
            return Json.mapper().readValue(response.body(), Page.class);
        } catch (IOException ex) {
            throw new EnergyDataException("decoding EIA response", ex);
        }
    }

    private String sanitize(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (!apiKey.isEmpty()) {
            message = message.replace(apiKey, "[redacted]");
        }
        return message;
    }

    private static String form(String... pairs) {
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Page {
        public Response response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Response {
        public String total;
        public List<EiaRecord> data;
    }
}
