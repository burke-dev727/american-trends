package energytracker.energy;

import java.util.Map;

public record TrendPoint(String period, Map<String, SourceTrend> sources) {}
