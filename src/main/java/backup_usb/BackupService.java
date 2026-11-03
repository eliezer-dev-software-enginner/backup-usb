package backup_usb;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.io.InputStream;
import java.io.OutputStream;

public final class BackupService {
    private BackupService() {}
    public static final class Cancelled extends IOException {
        public Cancelled() { super("Cópia interrompida pelo usuário."); }
    }
    public record Progress(int completed, int total) {
        public int percent() { return total == 0 ? 100 : (int) ((long) completed * 100 / total); }
        public String message() { return "Copiados " + completed + "/" + total + " arquivos (" + percent() + "%)."; }
    }

    public static void copy(Path source, Path destination, Path mount, Consumer<Progress> progress) throws IOException {
        copy(source, destination, mount, progress, () -> false);
    }

    public static void copy(Path source, Path destination, Path mount, Consumer<Progress> progress,
                            BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        source = source.toRealPath();
        mount = mount.toRealPath();
        destination = destination.toAbsolutePath().normalize();
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) throw new IOException("Origem inválida.");
        if (!destination.startsWith(mount) || destination.equals(mount)) throw new IOException("Destino deve ser uma subpasta do USB selecionado.");
        if (destination.startsWith(source) || source.startsWith(destination)) throw new IOException("Origem e destino não podem conter um ao outro.");
        for (Path parent = destination; parent != null && parent.startsWith(mount); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Destino contém link simbólico.");
        }
        List<Path> files = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                checkCancelled(cancelled);
                if (Files.isSymbolicLink(path)) throw new IOException("Origem contém link simbólico: " + path);
                if (Files.isRegularFile(path)) files.add(path);
                else if (Files.isDirectory(path)) directories.add(path);
                else throw new IOException("Arquivo especial não suportado: " + path);
            }
        }
        long required = 0;
        for (Path file : files) { checkCancelled(cancelled); required = Math.addExact(required, Files.size(file)); }
        if (Files.getFileStore(mount).getUsableSpace() < required) throw new IOException("Espaço insuficiente no USB: são necessários " + required + " bytes livres.");
        progress.accept(new Progress(0, files.size()));
        Files.createDirectories(destination);
        for (Path directory : directories) {
            checkCancelled(cancelled);
            Path targetDirectory = destination.resolve(source.relativize(directory));
            if (Files.isSymbolicLink(targetDirectory)) throw new IOException("Destino contém link simbólico: " + targetDirectory);
            Files.createDirectories(targetDirectory);
        }
        int done = 0;
        for (Path file : files) {
            checkCancelled(cancelled);
            Path target = destination.resolve(source.relativize(file));
            for (Path parent = target.getParent(); parent != null && parent.startsWith(destination); parent = parent.getParent()) {
                if (Files.isSymbolicLink(parent)) throw new IOException("Destino contém link simbólico: " + parent);
            }
            Files.createDirectories(target.getParent());
            if (Files.isSymbolicLink(target)) throw new IOException("Destino contém link simbólico: " + target);
            Path temporary = Files.createTempFile(target.getParent(), ".backup-", ".tmp");
            try {
                try (InputStream input = Files.newInputStream(file); OutputStream output = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[1024 * 1024];
                    for (int count; (count = input.read(buffer)) != -1;) {
                        checkCancelled(cancelled);
                        output.write(buffer, 0, count);
                    }
                }
                checkCancelled(cancelled);
                try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            progress.accept(new Progress(++done, files.size()));
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws Cancelled {
        if (cancelled.getAsBoolean()) throw new Cancelled();
    }
}
