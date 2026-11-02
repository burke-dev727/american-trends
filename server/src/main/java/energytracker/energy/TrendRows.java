package energytracker.energy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TrendRows {

    private TrendRows() {}

    record Row(String period, String source, SourceTrend trend) {}

    static String apiSourceKey(String source) {
        return "natural_gas".equals(source) ? "naturalGas" : source;
    }

    /** Folds rows sorted by period into one trend point per period. */
    static List<TrendPoint> group(List<Row> rows) {
        List<TrendPoint> points = new ArrayList<>();
        for (Row row : rows) {
            if (points.isEmpty() || !points.get(points.size() - 1).period().equals(row.period)) {
                points.add(new TrendPoint(row.period, new LinkedHashMap<>()));
            }
            points.get(points.size() - 1).sources().put(apiSourceKey(row.source), row.trend);
        }
        return points;
    }

    static SourceTrend trend(double generation, double share, Double rollingShare, Double yoyChange) {
        return new SourceTrend(generation, share, rollingShare, yoyChange);
    }

    static Double nullable(java.sql.ResultSet rows, int index) throws java.sql.SQLException {
        double value = rows.getDouble(index);
        return rows.wasNull() ? null : value;
    }
}
