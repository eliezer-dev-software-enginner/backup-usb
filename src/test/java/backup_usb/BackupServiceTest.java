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

    @Test void ignoresSymbolicLinksAndSpecialEntriesInSource() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        Path destination = mount.resolve("backup");
        Files.writeString(source.resolve("data.txt"), "data");
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(source.resolve("data-link.txt"), source.resolve("data.txt"));
        Files.createSymbolicLink(source.resolve("outside-link"), outside);
        Files.createSymbolicLink(source.resolve("broken-link"), root.resolve("missing"));
        var progress = new ArrayList<BackupService.Progress>();

        BackupService.copy(source, destination, mount, progress::add);

        assertEquals("data", Files.readString(destination.resolve("data.txt")));
        assertFalse(Files.exists(destination.resolve("data-link.txt"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(destination.resolve("outside-link"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(destination.resolve("broken-link"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertEquals(3, progress.getLast().skipped());
        assertTrue(progress.getLast().message().contains("3 links simbólicos/arquivos especiais ignorados."));
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

    @Test void reportsBytesBeforeFirstFileFinishes() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        Files.write(source.resolve("large.bin"), new byte[17 * 1024 * 1024]);
        var progress = new ArrayList<BackupService.Progress>();
        var phases = new ArrayList<String>();

        BackupService.copy(source, mount.resolve("backup"), mount, progress::add, () -> false, phases::add);

        assertTrue(progress.stream().anyMatch(item -> item.completed() == 0 && item.copiedBytes() >= 8L * 1024 * 1024));
        assertEquals(100, progress.getLast().percent());
        assertTrue(phases.stream().anyMatch(item -> item.contains("Aguardando o USB confirmar")));
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
