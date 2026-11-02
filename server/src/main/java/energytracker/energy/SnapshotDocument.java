package energytracker.energy;

import java.time.Instant;
import java.util.List;

/** A point-in-time copy of every dataset the API serves, so the deployed site doesn't wait on Databricks. */
public record SnapshotDocument(
        Instant createdAt,
        List<EnergyMix> mixes,
        List<TrendPoint> trends,
        List<Milestone> milestones,
        Forecast forecast) {

    public void validate() {
        if (mixes == null || mixes.isEmpty() || trends == null || trends.isEmpty()) {
            throw new IllegalArgumentException("snapshot has no energy mixes or trends");
        }
    }
}
