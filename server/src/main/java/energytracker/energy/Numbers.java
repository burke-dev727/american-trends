package energytracker.energy;

public final class Numbers {

    private Numbers() {}

    /** Rounds half away from zero, matching the previous server's math.Round. */
    public static double round(double value, int places) {
        double scale = Math.pow(10, places);
        double scaled = value * scale;
        return Math.copySign(Math.floor(Math.abs(scaled) + 0.5d), value) / scale;
    }
}
