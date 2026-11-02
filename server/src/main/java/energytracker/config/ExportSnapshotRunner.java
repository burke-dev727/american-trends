package energytracker.config;

import energytracker.energy.DatabricksEnergySource;
import energytracker.energy.DatabricksSettings;
import energytracker.energy.SnapshotExporter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("export")
public class ExportSnapshotRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportSnapshotRunner.class);

    private final AppProperties properties;

    public ExportSnapshotRunner(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> rest = args.getNonOptionArgs();
        String path = rest.size() > 1 ? rest.get(1) : "snapshot.json";
        DatabricksSettings settings = DatabricksSettings.from(properties.databricks());
        try (DatabricksEnergySource source = DatabricksEnergySource.open(settings)) {
            SnapshotExporter.export(source, Path.of(path), Instant.now());
        }
        log.info("Wrote snapshot of {} to {}", settings.schema(), path);
    }
}
