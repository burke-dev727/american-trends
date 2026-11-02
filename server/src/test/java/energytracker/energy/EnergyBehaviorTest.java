package energytracker.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EnergyBehaviorTest {

    @Test
    void cacheLoadsOnceWithinTtl() {
        AtomicInteger loads = new AtomicInteger();
        TtlCache<Integer> cache = new TtlCache<>(Duration.ofHours(1), () -> {
            loads.incrementAndGet();
            return 42;
        });
        assertEquals(42, cache.get());
        assertEquals(42, cache.get());
        assertEquals(1, loads.get());
    }

    @Test
    void cacheServesStaleValueWhenReloadFails() {
        boolean[] fail = {false};
        TtlCache<Integer> cache = new TtlCache<>(Duration.ZERO, () -> {
            if (fail[0]) {
                throw new IllegalStateException("warehouse unavailable");
            }
            return 42;
        });
        assertEquals(42, cache.get());
        fail[0] = true;
        assertEquals(42, cache.get());
    }

    @Test
    void cacheReturnsErrorWithoutCachedValue() {
        TtlCache<Integer> cache = new TtlCache<>(Duration.ofHours(1), () -> {
            throw new IllegalStateException("warehouse unavailable");
        });
        assertThrows(IllegalStateException.class, cache::get);
    }

    @Test
    void periodRangeIsInclusive() {
        List<EnergyMix> mixes = List.of(
                EnergyMix.empty("2024-01"), EnergyMix.empty("2024-02"), EnergyMix.empty("2024-03"));
        List<EnergyMix> got = Periods.inRange(mixes, "2024-02", "2024-03", EnergyMix::period);
        assertEquals(List.of("2024-02", "2024-03"), got.stream().map(EnergyMix::period).toList());
    }

    @Test
    void quotesIdentifiersAndRejectsUnsafeSchemaNames() {
        assertEquals(
                "`workspace`.`energy`.`gold_energy_trends`",
                DatabricksSettings.quoteIdentifier("workspace.energy.gold_energy_trends"));
        assertFalse(DatabricksSettings.safeSchema("workspace.energy; DROP TABLE x"));
        assertTrue(DatabricksSettings.safeSchema("workspace.energy"));
    }

    @Test
    void groupsTrendRowsByPeriod() {
        List<TrendRows.Row> rows = List.of(
                new TrendRows.Row("2024-01", "coal", new SourceTrend(0, 20, null, null)),
                new TrendRows.Row("2024-01", "natural_gas", new SourceTrend(0, 40, 40.5, null)),
                new TrendRows.Row("2024-02", "coal", new SourceTrend(0, 18, null, null)));
        List<TrendPoint> points = TrendRows.group(rows);
        assertEquals(2, points.size());
        assertEquals("2024-01", points.get(0).period());
        assertEquals(40, points.get(0).sources().get("naturalGas").share());
        assertEquals(40.5, points.get(0).sources().get("naturalGas").rollingShare());
        assertEquals(1, points.get(1).sources().size());
    }

    @Test
    void buildsSharesFromEiaRecords() {
        List<EnergyMix> mixes = EiaEnergySource.buildEnergyMixes(List.of(
                new EiaRecord("2024-02", "ALL", "200"),
                new EiaRecord("2024-02", "SUN", "50"),
                new EiaRecord("2024-01", "ALL", "100"),
                new EiaRecord("2024-01", "NG", "40"),
                new EiaRecord("2024-01", "NG", "99")));
        assertEquals(List.of("2024-01", "2024-02"), mixes.stream().map(EnergyMix::period).toList());
        assertEquals(40, mixes.get(0).naturalGas());
        assertEquals(25, mixes.get(1).solar());
        assertEquals(60, mixes.get(0).other());
    }

    @Test
    void snapshotRoundTrip(@TempDir Path dir) throws Exception {
        TrendSource source = sampleSource();
        Path path = dir.resolve("snapshot.json");
        Instant created = Instant.parse("2026-02-21T12:00:00Z");
        SnapshotExporter.export(source, path, created);

        SnapshotEnergySource loaded = SnapshotEnergySource.load(path);
        assertEquals(created, loaded.snapshot().createdAt());
        List<EnergyMix> mixes = loaded.energyMixes("2026-01", "2026-01");
        assertEquals(1, mixes.size());
        assertEquals(6, mixes.get(0).solar());
        assertEquals("2026-02", loaded.energyForecast().vintage());
        assertEquals(1, loaded.energyForecast().points().size());
        assertEquals(2, loaded.energyTrends("2025-12", "2026-01").size());
    }

    @Test
    void exportLeavesThePreviousFileWhenDataIsEmpty(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("snapshot.json");
        Files.writeString(path, "previous");
        TrendSource empty = new SampleSource(List.of(), List.of(), List.of(), Forecast.empty());
        assertThrows(IllegalArgumentException.class, () -> SnapshotExporter.export(empty, path, Instant.now()));
        assertEquals("previous", Files.readString(path));
    }

    public static TrendSource sampleSource() {
        return new SampleSource(
                List.of(mix("2025-12", 5), mix("2026-01", 6)),
                List.of(
                        point("2025-12", 10, 5, 190, 95),
                        point("2026-01", 12, 6, 188, 94)),
                List.of(),
                new Forecast("2026-02", List.of(point("2026-02", 20, 10, 180, 90))));
    }

    private static EnergyMix mix(String period, double solar) {
        return new EnergyMix(period, 0, 0, 0, 0, 0, solar, 0, 0);
    }

    private static TrendPoint point(String period, double solarTwh, double solarShare, double coalTwh, double coalShare) {
        return new TrendPoint(period, Map.of(
                "solar", new SourceTrend(solarTwh, solarShare, null, null),
                "coal", new SourceTrend(coalTwh, coalShare, null, null)));
    }

    private record SampleSource(
            List<EnergyMix> mixes, List<TrendPoint> points, List<Milestone> milestones, Forecast forecast)
            implements TrendSource {

        @Override
        public List<EnergyMix> energyMixes(String start, String end) {
            return Periods.inRange(mixes, start, end, EnergyMix::period);
        }

        @Override
        public List<TrendPoint> energyTrends(String start, String end) {
            return Periods.inRange(points, start, end, TrendPoint::period);
        }

        @Override
        public List<Milestone> energyMilestones() {
            return milestones;
        }

        @Override
        public Forecast energyForecast() {
            return forecast;
        }
    }
}
