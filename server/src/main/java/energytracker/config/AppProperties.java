package energytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String dataSource,
        String eiaApiKey,
        String snapshotPath,
        String staticDir,
        boolean trustProxyHeaders,
        Databricks databricks,
        OpenAi openai) {

    public record Databricks(String host, String httpPath, String token, String schema) {}

    public record OpenAi(String apiKey, String baseUrl, String model, String reasoningEffort) {}
}
