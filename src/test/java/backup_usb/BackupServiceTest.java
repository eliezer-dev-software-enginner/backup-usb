package backup_usb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
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
        BackupService.copy(source, destination, mount, ignored -> {});
        assertEquals("new", Files.readString(destination.resolve("data.txt")));
        assertTrue(Files.isDirectory(destination.resolve("empty")));
    }

    @Test void rejectsDestinationOutsideMount() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path mount = Files.createDirectory(root.resolve("usb"));
        assertThrows(IOException.class, () -> BackupService.copy(source, root.resolve("other"), mount, ignored -> {}));
    }
}
