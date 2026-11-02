package energytracker.config;

import energytracker.chat.ChatAgent;
import energytracker.energy.DatabricksEnergySource;
import energytracker.energy.DatabricksSettings;
import energytracker.energy.EiaEnergySource;
import energytracker.energy.EnergySource;
import energytracker.energy.SnapshotEnergySource;
import energytracker.energy.TrendSource;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!export")
public class ServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);

    @Bean
    EnergySource energySource(AppProperties properties) {
        String source = properties.dataSource() == null || properties.dataSource().isBlank()
                ? "eia"
                : properties.dataSource();
        return switch (source) {
            case "eia" -> {
                if (properties.eiaApiKey() == null || properties.eiaApiKey().isBlank()) {
                    log.warn("EIA_API_KEY is not set; requests to the EIA API will fail");
                }
                log.info("Data source: EIA API (live)");
                yield new EiaEnergySource(properties.eiaApiKey());
            }
            case "databricks" -> {
                DatabricksSettings settings = DatabricksSettings.from(properties.databricks());
                log.info("Data source: Databricks schema {}", settings.schema());
                yield DatabricksEnergySource.open(settings);
            }
            case "snapshot" -> {
                String path = properties.snapshotPath() == null || properties.snapshotPath().isBlank()
                        ? "snapshot.json"
                        : properties.snapshotPath();
                SnapshotEnergySource snapshot = SnapshotEnergySource.load(Path.of(path));
                log.info(
                        "Data source: snapshot {} created {}",
                        path,
                        snapshot.snapshot().createdAt());
                yield snapshot;
            }
            default -> throw new IllegalStateException(
                    "unknown DATA_SOURCE \"" + source + "\" (expected \"eia\", \"databricks\", or \"snapshot\")");
        };
    }

    @Bean
    ChatAgent chatAgent(AppProperties properties, EnergySource source) {
        AppProperties.OpenAi openai = properties.openai();
        String apiKey = openai == null ? "" : blankToEmpty(openai.apiKey());
        if (apiKey.isBlank()) {
            log.info("Chat assistant disabled: OPENAI_API_KEY is not set");
            return ChatAgent.disabled();
        }
        if (!(source instanceof TrendSource trends)) {
            log.info("Chat assistant disabled: it requires DATA_SOURCE=databricks or snapshot");
            return ChatAgent.disabled();
        }
        String baseUrl = openai.baseUrl() == null || openai.baseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : openai.baseUrl();
        String model = openai.model() == null || openai.model().isBlank() ? "gpt-6.1-sol" : openai.model();
        String effort = openai.reasoningEffort() == null || openai.reasoningEffort().isBlank()
                ? "low"
                : openai.reasoningEffort();
        log.info("Chat assistant enabled with model {}", model);
        return ChatAgent.enabled(apiKey, baseUrl, model, effort, trends, properties.trustProxyHeaders());
    }

    @Bean
    org.springframework.boot.ApplicationRunner listening(Environment environment, AppProperties properties) {
        return args -> {
            if (properties.staticDir() != null && !properties.staticDir().isBlank()) {
                log.info("Serving frontend from {}", properties.staticDir());
            }
            String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
            log.info("Energy API listening on http://localhost:{}", port);
        };
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
