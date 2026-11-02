package energytracker.energy;

import java.util.List;

/** Data sources that also serve precomputed trends, milestones, and the EIA forecast. */
public interface TrendSource extends EnergySource {

    List<TrendPoint> energyTrends(String start, String end);

    List<Milestone> energyMilestones();

    Forecast energyForecast();
}
