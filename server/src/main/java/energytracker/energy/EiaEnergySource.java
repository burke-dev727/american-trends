package energytracker.energy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Computes energy mixes on demand from raw EIA generation records. */
public final class EiaEnergySource implements EnergySource {

    private static final List<String> FUEL_TYPES = List.of("COW", "NG", "NUC", "HYC", "WND", "SUN", "PET");

    private final RecordFetcher records;

    public EiaEnergySource(String apiKey) {
        this(new EiaClient(apiKey)::fetch);
    }

    public EiaEnergySource(RecordFetcher records) {
        this.records = records;
    }

    @FunctionalInterface
    public interface RecordFetcher {
        List<EiaRecord> fetch(String start, String end);
    }

    @Override
    public List<EnergyMix> energyMixes(String start, String end) {
        return buildEnergyMixes(records.fetch(start, end));
    }

    static List<EnergyMix> buildEnergyMixes(List<EiaRecord> records) {
        Map<String, Map<String, Double>> byPeriod = new TreeMap<>();
        for (EiaRecord record : records) {
            Map<String, Double> fuels = byPeriod.computeIfAbsent(record.period, key -> new LinkedHashMap<>());
            if (fuels.containsKey(record.fueltypeid)) {
                continue;
            }
            fuels.put(record.fueltypeid, parseGeneration(record.generation));
        }
        List<EnergyMix> mixes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : byPeriod.entrySet()) {
            mixes.add(buildEnergyMix(entry.getValue(), entry.getKey()));
        }
        return mixes;
    }

    private static double parseGeneration(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static EnergyMix buildEnergyMix(Map<String, Double> fuels, String period) {
        double total = fuels.getOrDefault("ALL", 0.0);
        double other = total;
        for (String fuel : FUEL_TYPES) {
            other -= fuels.getOrDefault(fuel, 0.0);
        }
        return new EnergyMix(
                period,
                percentage(fuels.getOrDefault("COW", 0.0), total),
                percentage(fuels.getOrDefault("NG", 0.0), total),
                percentage(fuels.getOrDefault("NUC", 0.0), total),
                percentage(fuels.getOrDefault("HYC", 0.0), total),
                percentage(fuels.getOrDefault("WND", 0.0), total),
                percentage(fuels.getOrDefault("SUN", 0.0), total),
                percentage(fuels.getOrDefault("PET", 0.0), total),
                percentage(other, total));
    }

    private static double percentage(double value, double total) {
        if (total == 0) {
            return 0;
        }
        return Numbers.round(value / total * 100, 3);
    }
}
