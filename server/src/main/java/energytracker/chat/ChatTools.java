package energytracker.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import energytracker.energy.Forecast;
import energytracker.energy.Json;
import energytracker.energy.Numbers;
import energytracker.energy.Periods;
import energytracker.energy.SourceTrend;
import energytracker.energy.TrendPoint;
import energytracker.energy.TrendSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exposes the dashboard's data to the chat model as function tools. */
final class ChatTools {

    private static final Logger log = LoggerFactory.getLogger(ChatTools.class);
    private static final int MAX_MONTHLY_VALUES = 480;
    private static final List<String> SOURCE_KEYS =
            List.of("coal", "naturalGas", "nuclear", "hydro", "wind", "solar", "petroleum", "other");
    private static final ObjectMapper JSON = Json.mapper();

    private final TrendSource data;

    ChatTools(TrendSource data) {
        this.data = data;
    }

    List<FunctionTool> definitions() {
        Map<String, Object> sourcesParam = new LinkedHashMap<>();
        sourcesParam.put("type", List.of("array", "null"));
        sourcesParam.put("items", Map.of("type", "string", "enum", SOURCE_KEYS));
        sourcesParam.put("description", "Sources to include; null for all sources.");

        List<FunctionTool> tools = new ArrayList<>();
        tools.add(tool(
                "get_monthly_data",
                "Actual monthly U.S. electric power sector generation by source: generation (TWh), share of total (%), "
                        + "12-month average share (%), and year-over-year change in share (percentage points). "
                        + "months × sources is limited to " + MAX_MONTHLY_VALUES
                        + "; use get_annual_summary for long-term trends.",
                schema(Map.of(
                        "start", Map.of("type", "string", "description", "First month, YYYY-MM."),
                        "end", Map.of("type", "string", "description", "Last month, YYYY-MM."),
                        "sources", sourcesParam))));
        tools.add(tool(
                "get_annual_summary",
                "Calendar-year totals of actual generation by source (TWh) and each source's share of the year's total (%). "
                        + "actualMonths shows how many months of the year have data, so the latest year may be partial.",
                schema(Map.of(
                        "start_year", Map.of("type", "integer"),
                        "end_year", Map.of("type", "integer")))));
        tools.add(tool(
                "get_eia_forecast",
                "The U.S. EIA Short-Term Energy Outlook forecast for the months after the latest actual data, in the same "
                        + "monthly format as get_monthly_data, plus calendar-year totals that combine actual and forecast months.",
                schema(Map.of("sources", sourcesParam))));
        tools.add(tool(
                "get_milestones",
                "Months when one source's share first overtook another's and stayed ahead for at least 12 months.",
                schema(Map.of())));
        for (FunctionTool tool : tools) {
            tool.type = "function";
            tool.strict = true;
        }
        return tools;
    }

    /** Runs the named tool. Failures are reported to the model as {@code {"error": ...}}. */
    String call(String name, String arguments) {
        try {
            return encode(run(name, arguments));
        } catch (ToolArgumentException ex) {
            return encode(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            log.warn("chat tool {} failed: {}", name, ex.toString());
            return encode(Map.of("error", "The data source is temporarily unavailable."));
        }
    }

    Coverage coverage() {
        List<TrendPoint> points = data.energyTrends("0000-01", "9999-12");
        Coverage coverage = new Coverage();
        if (!points.isEmpty()) {
            coverage.firstPeriod = points.get(0).period();
            coverage.lastActualPeriod = points.get(points.size() - 1).period();
        }
        Forecast forecast = data.energyForecast();
        if (forecast.points() != null && !forecast.points().isEmpty()) {
            coverage.forecastVintage = forecast.vintage() == null ? "" : forecast.vintage();
            coverage.forecastEnd = forecast.points().get(forecast.points().size() - 1).period();
        }
        return coverage;
    }

    private Object run(String name, String arguments) throws ToolArgumentException {
        return switch (name) {
            case "get_monthly_data" -> {
                MonthlyArgs args = decode(arguments, MonthlyArgs.class);
                yield monthlyData(args.start, args.end, args.sources);
            }
            case "get_annual_summary" -> {
                AnnualArgs args = decode(arguments, AnnualArgs.class);
                yield annualSummary(args.startYear, args.endYear);
            }
            case "get_eia_forecast" -> {
                SourcesArgs args = decode(arguments, SourcesArgs.class);
                yield forecast(args.sources);
            }
            case "get_milestones" -> data.energyMilestones();
            default -> throw new ToolArgumentException("unknown tool \"" + name + "\"");
        };
    }

    private Object monthlyData(String start, String end, List<String> requested) throws ToolArgumentException {
        if (!Periods.valid(start) || !Periods.valid(end) || start.compareTo(end) > 0) {
            throw new ToolArgumentException("start and end must be YYYY-MM with start <= end");
        }
        List<String> sources = validSources(requested);
        List<TrendPoint> points = data.energyTrends(start, end);
        if (points.size() * sources.size() > MAX_MONTHLY_VALUES) {
            throw new ToolArgumentException(points.size() + " months × " + sources.size()
                    + " sources exceeds " + MAX_MONTHLY_VALUES
                    + " values; narrow the range or sources, or use get_annual_summary");
        }
        return Map.of("months", monthlyRows(points, sources));
    }

    private Object annualSummary(int startYear, int endYear) throws ToolArgumentException {
        if (startYear > endYear) {
            throw new ToolArgumentException("start_year must be <= end_year");
        }
        List<TrendPoint> points = data.energyTrends(String.format("%04d-01", startYear), String.format("%04d-12", endYear));
        return Map.of("years", annualSummaries(points, null, SOURCE_KEYS));
    }

    private Object forecast(List<String> requested) throws ToolArgumentException {
        List<String> sources = validSources(requested);
        Forecast forecast = data.energyForecast();
        if (forecast.points() == null || forecast.points().isEmpty()) {
            return Map.of("error", "no EIA forecast is available");
        }
        String first = forecast.points().get(0).period();
        String last = forecast.points().get(forecast.points().size() - 1).period();
        List<TrendPoint> actual = new ArrayList<>(data.energyTrends(first.substring(0, 4) + "-01", first));
        actual.removeIf(point -> point.period().compareTo(first) >= 0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "U.S. EIA Short-Term Energy Outlook");
        body.put("vintage", forecast.vintage());
        body.put("forecastPeriod", first + " to " + last);
        body.put("months", monthlyRows(forecast.points(), sources));
        body.put("annualTotals", annualSummaries(actual, forecast.points(), sources));
        return body;
    }

    private static List<String> validSources(List<String> sources) throws ToolArgumentException {
        if (sources == null || sources.isEmpty()) {
            return SOURCE_KEYS;
        }
        for (String source : sources) {
            if (!SOURCE_KEYS.contains(source)) {
                throw new ToolArgumentException("unknown source \"" + source + "\"");
            }
        }
        return sources;
    }

    private static <T> T decode(String arguments, Class<T> type) throws ToolArgumentException {
        try {
            return JSON.readValue(arguments, type);
        } catch (JsonProcessingException ex) {
            throw new ToolArgumentException("arguments must be a JSON object matching the tool schema");
        }
    }

    private static String encode(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"failed to encode tool result\"}";
        }
    }

    private static FunctionTool tool(String name, String description, Map<String, Object> parameters) {
        FunctionTool tool = new FunctionTool();
        tool.name = name;
        tool.description = description;
        tool.parameters = parameters;
        return tool;
    }

    private static Map<String, Object> schema(Map<String, Object> properties) {
        List<String> required = new ArrayList<>(properties.keySet());
        Collections.sort(required);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static List<Map<String, Object>> monthlyRows(List<TrendPoint> points, List<String> sources) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TrendPoint point : points) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", point.period());
            Map<String, SourceTrend> bySource = point.sources() == null ? Map.of() : point.sources();
            for (String source : sources) {
                SourceTrend trend = bySource.get(source);
                if (trend == null) {
                    continue;
                }
                row.put(source, new MonthlyValue(
                        Numbers.round(trend.generation(), 2),
                        Numbers.round(trend.share(), 2),
                        roundNullable(trend.rollingShare(), 2),
                        roundNullable(trend.yoyChange(), 2)));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Totals generation by calendar year. Only {@code sources} are listed, but shares use every source.
     * Points in {@code forecast} count as forecast months. Both lists must be sorted by period.
     */
    static List<AnnualTotal> annualSummaries(List<TrendPoint> actual, List<TrendPoint> forecast, List<String> sources) {
        List<AnnualTotal> years = new ArrayList<>();
        Map<Integer, Map<String, Double>> generation = new LinkedHashMap<>();
        add(actual, false, years, generation);
        add(forecast, true, years, generation);
        for (AnnualTotal year : years) {
            Map<String, Double> bySource = generation.get(year.year);
            double total = 0;
            for (double twh : bySource.values()) {
                total += twh;
            }
            year.totalTwh = Numbers.round(total, 1);
            for (Map.Entry<String, Double> entry : bySource.entrySet()) {
                if (!sources.contains(entry.getKey())) {
                    continue;
                }
                double share = total > 0 ? entry.getValue() / total * 100 : 0;
                year.sources.put(entry.getKey(), new SourceTotal(
                        Numbers.round(entry.getValue(), 1), Numbers.round(share, 2)));
            }
        }
        return years;
    }

    private static void add(
            List<TrendPoint> points,
            boolean isForecast,
            List<AnnualTotal> years,
            Map<Integer, Map<String, Double>> generation) {
        if (points == null) {
            return;
        }
        for (TrendPoint point : points) {
            int year;
            try {
                year = Integer.parseInt(point.period().substring(0, 4));
            } catch (RuntimeException ex) {
                continue;
            }
            if (years.isEmpty() || years.get(years.size() - 1).year != year) {
                AnnualTotal total = new AnnualTotal();
                total.year = year;
                years.add(total);
                generation.put(year, new LinkedHashMap<>());
            }
            AnnualTotal total = years.get(years.size() - 1);
            if (isForecast) {
                total.forecastMonths++;
            } else {
                total.actualMonths++;
            }
            if (point.sources() == null) {
                continue;
            }
            Map<String, Double> bySource = generation.get(year);
            for (Map.Entry<String, SourceTrend> entry : point.sources().entrySet()) {
                bySource.merge(entry.getKey(), entry.getValue().generation(), Double::sum);
            }
        }
    }

    private static Double roundNullable(Double value, int places) {
        return value == null ? null : Numbers.round(value, places);
    }

    static final class Coverage {
        String firstPeriod = "";
        String lastActualPeriod = "";
        String forecastVintage = "";
        String forecastEnd = "";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MonthlyValue(double twh, double share, Double share12moAvg, Double yoyChangePts) {}

    record SourceTotal(double twh, double share) {}

    static final class AnnualTotal {
        public int year;
        public int actualMonths;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public int forecastMonths;

        public double totalTwh;
        public Map<String, SourceTotal> sources = new LinkedHashMap<>();
    }

    static final class MonthlyArgs {
        public String start;
        public String end;
        public List<String> sources;
    }

    static final class AnnualArgs {
        @JsonProperty("start_year")
        public int startYear;

        @JsonProperty("end_year")
        public int endYear;
    }

    static final class SourcesArgs {
        public List<String> sources;
    }

    static final class ToolArgumentException extends Exception {
        ToolArgumentException(String message) {
            super(message);
        }
    }
}
