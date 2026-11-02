package energytracker.energy;

public record EnergyMix(
        String period,
        double coal,
        double naturalGas,
        double nuclear,
        double hydro,
        double wind,
        double solar,
        double petroleum,
        double other) {

    public static EnergyMix empty(String period) {
        return new EnergyMix(period, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static EnergyMix sample() {
        return new EnergyMix("2000-01", 51.2, 15.4, 20.1, 7.3, 0.1, 0.01, 3.0, 2.89);
    }
}
