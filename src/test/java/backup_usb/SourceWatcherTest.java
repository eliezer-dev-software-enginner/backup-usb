package backup_usb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SourceWatcherTest {
    @TempDir Path root;

    @Test void detectsChangesInExistingSubdirectories() throws Exception {
        Path nested = Files.createDirectories(root.resolve("nested"));
        CountDownLatch changed = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        try (SourceWatcher ignored = SourceWatcher.start(root, changed::countDown, failure::set)) {
            Files.writeString(nested.resolve("new.txt"), "new data");
            assertTrue(changed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
        }
    }

    @Test void watchesTreeContainingSymbolicLinks() throws Exception {
        Path watched = Files.createDirectory(root.resolve("watched"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(watched.resolve("outside-link"), outside);
        Files.createSymbolicLink(watched.resolve("broken-link"), root.resolve("missing"));
        CountDownLatch changed = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();

        try (SourceWatcher ignored = SourceWatcher.start(watched, changed::countDown, failure::set)) {
            Files.writeString(watched.resolve("new.txt"), "new data");
            assertTrue(changed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
        }
    }

    @Test void registersNewSubdirectories() throws Exception {
        CountDownLatch createdDirectory = new CountDownLatch(1);
        CountDownLatch changedInside = new CountDownLatch(2);
        try (SourceWatcher ignored = SourceWatcher.start(root, () -> {
            createdDirectory.countDown();
            changedInside.countDown();
        }, error -> fail(error))) {
            Path nested = Files.createDirectory(root.resolve("created-later"));
            assertTrue(createdDirectory.await(5, TimeUnit.SECONDS));
            Files.writeString(nested.resolve("file.txt"), "content");
            assertTrue(changedInside.await(5, TimeUnit.SECONDS));
        }
    }
}
