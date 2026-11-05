package backup_usb;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/** Watches a source tree; the caller coalesces events and reconciles the whole tree. */
public final class SourceWatcher implements AutoCloseable {
    private final WatchService service;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final Runnable changed;
    private final Consumer<Exception> failed;

    private SourceWatcher(Path root, Runnable changed, Consumer<Exception> failed) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        this.changed = changed;
        this.failed = failed;
        try { registerTree(root.toRealPath()); }
        catch (IOException | RuntimeException e) { service.close(); throw e; }
    }

    public static SourceWatcher start(Path root, Runnable changed, Consumer<Exception> failed) throws IOException {
        SourceWatcher watcher = new SourceWatcher(root, changed, failed);
        Thread.startVirtualThread(watcher::listen);
        return watcher;
    }

    private void registerTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) continue;
                if (Files.isDirectory(path)) directories.put(path.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE), path);
            }
        }
    }

    private void listen() {
        try {
            while (true) {
                WatchKey key = service.take();
                Path directory = directories.get(key);
                if (directory == null) { key.reset(); continue; }
                boolean dirty = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    dirty = true;
                    if (event.kind() == ENTRY_CREATE) {
                        Path child = directory.resolve((Path) event.context());
                        if (Files.isDirectory(child) && !Files.isSymbolicLink(child)) registerTree(child);
                    }
                }
                if (!key.reset()) directories.remove(key);
                if (dirty) changed.run();
            }
        } catch (ClosedWatchServiceException ignored) {
            // Normal shutdown after replacing settings or closing the application.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) { failed.accept(e); }
    }

    @Override public void close() throws IOException { service.close(); }
}
