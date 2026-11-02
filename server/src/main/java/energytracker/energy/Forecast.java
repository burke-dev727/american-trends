package energytracker.energy;

import java.util.List;

/** Projected months following the latest actual data. {@code vintage} is the YYYY-MM the forecast was retrieved. */
public record Forecast(String vintage, List<TrendPoint> points) {

    public static Forecast empty() {
        return new Forecast("", List.of());
    }
}
