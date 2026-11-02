package energytracker.energy;

import energytracker.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record DatabricksSettings(String host, String httpPath, String token, String schema) {

    public static final String DEFAULT_SCHEMA = "workspace.energy";

    private static final Pattern SCHEMA_NAME = Pattern.compile("^\\w+(\\.\\w+)?$");

    public static DatabricksSettings from(AppProperties.Databricks config) {
        String host = config == null || config.host() == null ? "" : config.host();
        host = host.replaceFirst("^https://", "");
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String httpPath = config == null || config.httpPath() == null ? "" : config.httpPath();
        String token = config == null || config.token() == null ? "" : config.token();
        String schema = config == null || config.schema() == null || config.schema().isBlank()
                ? DEFAULT_SCHEMA
                : config.schema();

        List<String> missing = new ArrayList<>();
        if (host.isBlank()) {
            missing.add("DATABRICKS_HOST");
        }
        if (httpPath.isBlank()) {
            missing.add("DATABRICKS_HTTP_PATH");
        }
        if (token.isBlank()) {
            missing.add("DATABRICKS_TOKEN");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("DATA_SOURCE=databricks requires " + String.join(", ", missing));
        }
        if (!SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalStateException(
                    "DATABRICKS_SCHEMA \"" + schema + "\" must look like catalog.schema");
        }
        return new DatabricksSettings(host, httpPath, token, schema);
    }

    public static boolean safeSchema(String schema) {
        return schema != null && SCHEMA_NAME.matcher(schema).matches();
    }

    /** Backtick-quotes each part of a dotted name made of word characters. */
    public static String quoteIdentifier(String name) {
        String[] parts = name.split("\\.", -1);
        StringBuilder quoted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                quoted.append('.');
            }
            quoted.append('`').append(parts[i]).append('`');
        }
        return quoted.toString();
    }
}
