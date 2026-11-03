package backup_usb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {
    @TempDir Path root;

    @Test void replacesFilesAndPreservesEmptyDirectories() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        Path destination = Files.createDirectory(mount.resolve("backup"));
        Files.writeString(source.resolve("data.txt"), "new");
        Files.createDirectory(source.resolve("empty"));
        Files.writeString(destination.resolve("data.txt"), "old");
        Files.setLastModifiedTime(destination.resolve("data.txt"), Files.getLastModifiedTime(source.resolve("data.txt")));
        var progress = new ArrayList<BackupService.Progress>();
        BackupService.copy(source, destination, mount, progress::add);
        assertEquals("new", Files.readString(destination.resolve("data.txt")));
        assertTrue(Files.isDirectory(destination.resolve("empty")));
        assertEquals(0, progress.getFirst().percent());
        assertEquals(100, progress.getLast().percent());
    }

    @Test void rejectsDestinationOutsideMount() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        assertThrows(IOException.class, () -> BackupService.copy(source, root.resolve("other"), mount, ignored -> {}));
    }

    @Test void cancellationKeepsExistingTargetAndRemovesTemporaryFile() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        Path destination = Files.createDirectory(mount.resolve("backup"));
        Files.write(source.resolve("data.bin"), new byte[2 * 1024 * 1024]);
        Files.writeString(destination.resolve("data.bin"), "original");
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(BackupService.Cancelled.class, () -> BackupService.copy(source, destination, mount,
                ignored -> {}, () -> checks.incrementAndGet() > 7));
        assertEquals("original", Files.readString(destination.resolve("data.bin")));
        try (var entries = Files.list(destination)) { assertEquals(1, entries.count()); }
    }

    @Test void skipsUnchangedFilesAndCopiesOnlyNewOrModifiedOnes() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        Path destination = mount.resolve("backup");
        Files.writeString(source.resolve("one.txt"), "first");
        BackupService.copy(source, destination, mount, ignored -> {});
        Path target = destination.resolve("one.txt");
        var originalModified = Files.getLastModifiedTime(target);

        var second = new ArrayList<BackupService.Progress>();
        BackupService.copy(source, destination, mount, second::add);
        assertEquals(0, second.getLast().total());
        assertEquals(1, second.getLast().unchanged());
        assertEquals(originalModified, Files.getLastModifiedTime(target));

        Files.writeString(source.resolve("one.txt"), "updated content");
        Files.writeString(source.resolve("two.txt"), "new");
        var third = new ArrayList<BackupService.Progress>();
        BackupService.copy(source, destination, mount, third::add);
        assertEquals(2, third.getLast().total());
        assertEquals("updated content", Files.readString(target));
        assertEquals("new", Files.readString(destination.resolve("two.txt")));
    }
}
