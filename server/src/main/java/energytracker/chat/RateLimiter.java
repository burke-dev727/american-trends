package energytracker.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/** Allows up to {@code limit} requests per key in each fixed window. */
final class RateLimiter {

    private final int limit;
    private final Duration window;
    Supplier<Instant> now = Instant::now;
    private final Map<String, Window> counts = new HashMap<>();

    RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    synchronized boolean allow(String key) {
        Instant current = now.get();
        Window count = counts.get(key);
        if (count == null || Duration.between(count.start, current).compareTo(window) >= 0) {
            count = new Window(current, 0);
        }
        if (count.n >= limit) {
            counts.put(key, count);
            return false;
        }
        count.n++;
        counts.put(key, count);
        if (counts.size() > 10_000) {
            Iterator<Map.Entry<String, Window>> entries = counts.entrySet().iterator();
            while (entries.hasNext()) {
                Window windowCount = entries.next().getValue();
                if (Duration.between(windowCount.start, current).compareTo(window) >= 0) {
                    entries.remove();
                }
            }
        }
        return true;
    }

    private static final class Window {
        private final Instant start;
        private int n;

        private Window(Instant start, int n) {
            this.start = start;
            this.n = n;
        }
    }
}
