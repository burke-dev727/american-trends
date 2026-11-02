package energytracker.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads KEY=VALUE pairs from a dotenv file. Variables already set in the
 * environment or as system properties take precedence, and a missing file is ignored.
 * Spring reads system properties, which is how these values reach {@code application.yaml}.
 */
public final class EnvFile {

    private EnvFile() {}

    public static void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException ex) {
            throw new IllegalStateException("loading " + path, ex);
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = stripQuotes(line.substring(split + 1).trim());
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
