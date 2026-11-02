package energytracker.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Periods {

    public static final String INVALID = "Period must be in YYYY-MM format";

    private static final Pattern PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private Periods() {}

    public static boolean valid(String period) {
        return period != null && PERIOD.matcher(period).matches();
    }

    public static <T> List<T> inRange(List<T> items, String start, String end, Function<T, String> period) {
        List<T> filtered = new ArrayList<>();
        if (items == null) {
            return filtered;
        }
        for (T item : items) {
            String value = period.apply(item);
            if (value != null && value.compareTo(start) >= 0 && value.compareTo(end) <= 0) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
