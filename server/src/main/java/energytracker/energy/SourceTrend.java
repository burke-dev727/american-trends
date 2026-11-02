package energytracker.energy;

public record SourceTrend(double generation, double share, Double rollingShare, Double yoyChange) {}
