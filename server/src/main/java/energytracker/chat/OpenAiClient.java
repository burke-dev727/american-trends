package energytracker.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import energytracker.energy.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

final class OpenAiClient implements ResponseStreamer {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = Json.mapper();

    OpenAiClient(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }

    OpenAiClient(String apiKey, String baseUrl, HttpClient http) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    @Override
    public void stream(ResponsesRequest request, Instant deadline, ResponseListener listener) throws IOException {
        request.stream = true;
        byte[] body = mapper.writeValueAsBytes(request);
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofMillis(1);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(remaining)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<java.io.InputStream> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("calling OpenAI", ex);
        }
        try (java.io.InputStream bodyStream = response.body()) {
            if (response.statusCode() != 200) {
                byte[] errorBody = bodyStream.readNBytes(64 * 1024);
                throw new IOException("OpenAI returned " + response.statusCode() + ": " + errorMessage(errorBody));
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                ServerSentEvents.read(reader, data -> {
                    ResponseEvent event = mapper.readValue(data, ResponseEvent.class);
                    listener.onEvent(event);
                });
            }
        }
    }

    private String errorMessage(byte[] body) {
        try {
            String message = mapper.readTree(body).path("error").path("message").asText("");
            return message == null ? "" : message;
        } catch (IOException ignored) {
            return "";
        }
    }
}
