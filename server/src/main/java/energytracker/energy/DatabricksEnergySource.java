package energytracker.energy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Serves the gold tables built by the Databricks energy-etl job, cached for an hour. */
public final class DatabricksEnergySource implements TrendSource, AutoCloseable {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int QUERY_TIMEOUT_SECONDS = 120;
    private static final String DRIVER = "com.databricks.client.jdbc.Driver";

    private final Connection connection;
    private final TtlCache<List<EnergyMix>> mixes;
    private final TtlCache<List<TrendPoint>> trends;
    private final TtlCache<List<Milestone>> milestones;
    private final TtlCache<Forecast> forecast;

    private DatabricksEnergySource(String schema, Connection connection) {
        this.connection = connection;
        String mixTable = table(schema, "gold_energy_mix_monthly");
        String trendTable = table(schema, "gold_energy_trends");
        String milestoneTable = table(schema, "gold_energy_milestones");
        String forecastTable = table(schema, "gold_energy_forecast");
        this.mixes = new TtlCache<>(CACHE_TTL, () -> queryMixes(
                "SELECT period, coal, natural_gas, nuclear, hydro, wind, solar, petroleum, other FROM "
                        + mixTable + " ORDER BY period"));
        this.trends = new TtlCache<>(CACHE_TTL, () -> queryTrends(
                "SELECT period, source, generation_twh, share, share_12mo_avg, yoy_change_pts FROM "
                        + trendTable + " ORDER BY period, source"));
        this.milestones = new TtlCache<>(CACHE_TTL, () -> queryMilestones(
                "SELECT period, source, passed_source, share, description FROM "
                        + milestoneTable + " ORDER BY period, source"));
        this.forecast = new TtlCache<>(CACHE_TTL, () -> queryForecast(
                "SELECT vintage, period, source, generation_twh, share, share_12mo_avg, yoy_change_pts FROM "
                        + forecastTable + " ORDER BY period, source"));
    }

    public static DatabricksEnergySource open(DatabricksSettings settings) {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Databricks JDBC driver is not on the classpath", ex);
        }
        Properties properties = new Properties();
        properties.setProperty("transportMode", "http");
        properties.setProperty("ssl", "1");
        properties.setProperty("AuthMech", "3");
        properties.setProperty("httpPath", settings.httpPath());
        properties.setProperty("UID", "token");
        properties.setProperty("PWD", settings.token());
        String url = "jdbc:databricks://" + settings.host() + ":443";
        try {
            Connection connection = DriverManager.getConnection(url, properties);
            connection.setReadOnly(true);
            return new DatabricksEnergySource(settings.schema(), connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("configuring Databricks connection: " + ex.getMessage(), ex);
        }
    }

    private static String table(String schema, String name) {
        return DatabricksSettings.quoteIdentifier(schema + "." + name);
    }

    @Override
    public List<EnergyMix> energyMixes(String start, String end) {
        return Periods.inRange(mixes.get(), start, end, EnergyMix::period);
    }

    @Override
    public List<TrendPoint> energyTrends(String start, String end) {
        return Periods.inRange(trends.get(), start, end, TrendPoint::period);
    }

    @Override
    public List<Milestone> energyMilestones() {
        return milestones.get();
    }

    @Override
    public Forecast energyForecast() {
        return forecast.get();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private synchronized <T> List<T> query(String sql, RowReader<T> reader) {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rows = statement.executeQuery(sql)) {
                List<T> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(reader.read(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new EnergyDataException("querying Databricks: " + ex.getMessage(), ex);
        }
    }

    private List<EnergyMix> queryMixes(String sql) {
        return query(sql, rows -> new EnergyMix(
                rows.getString(1),
                rows.getDouble(2),
                rows.getDouble(3),
                rows.getDouble(4),
                rows.getDouble(5),
                rows.getDouble(6),
                rows.getDouble(7),
                rows.getDouble(8),
                rows.getDouble(9)));
    }

    private List<TrendPoint> queryTrends(String sql) {
        return TrendRows.group(query(sql, rows -> readTrend(rows, 1)));
    }

    private Forecast queryForecast(String sql) {
        String[] vintage = {""};
        List<TrendRows.Row> rows = query(sql, row -> {
            vintage[0] = row.getString(1);
            return readTrend(row, 2);
        });
        return new Forecast(vintage[0], TrendRows.group(rows));
    }

    private List<Milestone> queryMilestones(String sql) {
        return query(sql, rows -> new Milestone(
                rows.getString(1),
                TrendRows.apiSourceKey(rows.getString(2)),
                TrendRows.apiSourceKey(rows.getString(3)),
                rows.getDouble(4),
                rows.getString(5)));
    }

    private static TrendRows.Row readTrend(ResultSet rows, int firstColumn) throws SQLException {
        return new TrendRows.Row(
                rows.getString(firstColumn),
                rows.getString(firstColumn + 1),
                new SourceTrend(
                        rows.getDouble(firstColumn + 2),
                        rows.getDouble(firstColumn + 3),
                        TrendRows.nullable(rows, firstColumn + 4),
                        TrendRows.nullable(rows, firstColumn + 5)));
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rows) throws SQLException;
    }
}
