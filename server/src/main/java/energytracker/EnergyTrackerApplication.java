package energytracker;

import energytracker.config.AppProperties;
import energytracker.config.EnvFile;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EnergyTrackerApplication {

    public static void main(String[] args) {
        EnvFile.load(Path.of(".env"));
        boolean export = args.length > 0 && "export-snapshot".equals(args[0]);
        SpringApplication app = new SpringApplication(EnergyTrackerApplication.class);
        if (export) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles("export");
        }
        var context = app.run(args);
        if (export) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
