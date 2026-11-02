package energytracker.energy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SnapshotEnergySource implements TrendSource {

    private final SnapshotDocument snapshot;

    private SnapshotEnergySource(SnapshotDocument snapshot) {
        this.snapshot = snapshot;
    }

    public static SnapshotEnergySource load(Path path) {
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("reading snapshot: " + ex.getMessage(), ex);
        }
        SnapshotDocument snapshot;
        try {
            snapshot = Json.mapper().readValue(data, SnapshotDocument.class);
        } catch (IOException ex) {
            throw new IllegalStateException("decoding snapshot " + path + ": " + ex.getMessage(), ex);
        }
        try {
            snapshot.validate();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("snapshot " + path + ": " + ex.getMessage(), ex);
        }
        return new SnapshotEnergySource(snapshot);
    }

    public SnapshotDocument snapshot() {
        return snapshot;
    }

    @Override
    public List<EnergyMix> energyMixes(String start, String end) {
        return Periods.inRange(snapshot.mixes(), start, end, EnergyMix::period);
    }

    @Override
    public List<TrendPoint> energyTrends(String start, String end) {
        return Periods.inRange(snapshot.trends(), start, end, TrendPoint::period);
    }

    @Override
    public List<Milestone> energyMilestones() {
        return snapshot.milestones() == null ? List.of() : snapshot.milestones();
    }

    @Override
    public Forecast energyForecast() {
        return snapshot.forecast() == null ? Forecast.empty() : snapshot.forecast();
    }
}
