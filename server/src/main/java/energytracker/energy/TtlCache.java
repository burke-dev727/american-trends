package energytracker.energy;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the result of {@code load} in memory and reloads it once the TTL expires.
 * If a reload fails, the previous value is served until the next attempt.
 */
public final class TtlCache<T> {

    private static final Logger log = LoggerFactory.getLogger(TtlCache.class);

    private final Duration ttl;
    private final Supplier<T> load;
    private T value;
    private Instant loadedAt;

    public TtlCache(Duration ttl, Supplier<T> load) {
        this.ttl = ttl;
        this.load = load;
    }

    public synchronized T get() {
        if (loadedAt != null && !ttl.isZero() && Duration.between(loadedAt, Instant.now()).compareTo(ttl) < 0) {
            return value;
        }
        try {
            T loaded = load.get();
            value = loaded;
            loadedAt = Instant.now();
            return loaded;
        } catch (RuntimeException ex) {
            if (loadedAt == null) {
                throw ex;
            }
            log.warn("refreshing cached data failed, serving previous copy: {}", ex.toString());
            return value;
        }
    }
}
