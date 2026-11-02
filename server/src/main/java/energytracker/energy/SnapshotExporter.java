package energytracker.energy;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

public final class SnapshotExporter {

    private SnapshotExporter() {}

    /**
     * Reads every dataset from {@code source} and writes it to {@code path},
     * replacing any existing file only once the new one is complete.
     */
    public static void export(TrendSource source, Path path, Instant createdAt) throws IOException {
        SnapshotDocument snapshot = new SnapshotDocument(
                createdAt,
                source.energyMixes("0000-01", "9999-12"),
                source.energyTrends("0000-01", "9999-12"),
                source.energyMilestones(),
                source.energyForecast());
        snapshot.validate();

        Path directory = path.getParent() == null ? Path.of(".") : path.getParent();
        Path tmp = Files.createTempFile(directory, ".snapshot-", ".json");
        try {
            Files.write(tmp, Json.mapper().writeValueAsBytes(snapshot));
            try {
                Files.setPosixFilePermissions(tmp, worldReadable());
            } catch (UnsupportedOperationException ignored) {
                // Windows has no POSIX mode bits. The file is still readable by the container build.
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (RuntimeException | IOException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
    }

    private static Set<PosixFilePermission> worldReadable() {
        return EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ);
    }
}
