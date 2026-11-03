package backup_usb;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;

public final class BackupService {
    private BackupService() {}
    public static final class Cancelled extends IOException {
        public Cancelled() { super("Cópia interrompida pelo usuário."); }
    }
    public record Progress(int completed, int total, int unchanged, long copiedBytes, long totalBytes) {
        public int percent() { return totalBytes == 0 ? 100 : (int) (copiedBytes * 100.0 / totalBytes); }
        public String message() {
            return total == 0 ? "Nenhuma alteração; " + unchanged + " arquivos já atualizados (100%)."
                    : "Copiados " + completed + "/" + total + " arquivos; " + copiedBytes / (1024 * 1024) + "/" + totalBytes / (1024 * 1024) + " MiB (" + percent() + "%). "
                    + unchanged + " sem alterações.";
        }
    }

    private record Planned(Path source, Path target, long size, long previousSize, FileTime modified) {}

    public static void copy(Path source, Path destination, Path mount, Consumer<Progress> progress) throws IOException {
        copy(source, destination, mount, progress, () -> false, ignored -> {});
    }

    public static void copy(Path source, Path destination, Path mount, Consumer<Progress> progress,
                            BooleanSupplier cancelled) throws IOException {
        copy(source, destination, mount, progress, cancelled, ignored -> {});
    }

    public static void copy(Path source, Path destination, Path mount, Consumer<Progress> progress,
                            BooleanSupplier cancelled, Consumer<String> scanStatus) throws IOException {
        checkCancelled(cancelled);
        if (Files.isSymbolicLink(source)) throw new IOException("Origem inválida: link simbólico.");
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
        List<Planned> planned = new ArrayList<>();
        int unchanged = 0;
        long growth = 0;
        long largestTemporary = 0;
        long totalBytes = 0;
        scanStatus.accept("Comparando 0/" + files.size() + " arquivos...");
        int scanned = 0;
        for (Path file : files) {
            checkCancelled(cancelled);
            Path target = destination.resolve(source.relativize(file));
            checkTarget(target, destination);
            long size = Files.size(file);
            FileTime modified = Files.getLastModifiedTime(file);
            long previousSize = Files.exists(target) ? Files.size(target) : 0;
            if (Files.isRegularFile(target) && previousSize == size
                    && sameContent(file, target, cancelled)) {
                unchanged++;
            } else {
                planned.add(new Planned(file, target, size, previousSize, modified));
                totalBytes = Math.addExact(totalBytes, size);
                growth = Math.addExact(growth, Math.max(0, size - previousSize));
                largestTemporary = Math.max(largestTemporary, size);
            }
            scanned++;
            if (scanned % 100 == 0 || scanned == files.size())
                scanStatus.accept("Comparados " + scanned + "/" + files.size() + " arquivos...");
        }
        long required = Math.addExact(growth, largestTemporary);
        if (Files.getFileStore(mount).getUsableSpace() < required) throw new IOException("Espaço insuficiente no USB: são necessários " + required + " bytes livres para esta sincronização.");
        progress.accept(new Progress(0, planned.size(), unchanged, 0, totalBytes));
        Files.createDirectories(destination);
        for (Path directory : directories) {
            checkCancelled(cancelled);
            Path targetDirectory = destination.resolve(source.relativize(directory));
            if (Files.isSymbolicLink(targetDirectory)) throw new IOException("Destino contém link simbólico: " + targetDirectory);
            Files.createDirectories(targetDirectory);
        }
        int done = 0;
        long copiedBytes = 0;
        long lastReportedBytes = 0;
        for (Planned item : planned) {
            checkCancelled(cancelled);
            Path target = item.target();
            checkTarget(target, destination);
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".backup-", ".tmp");
            try {
                try (InputStream input = Files.newInputStream(item.source()); OutputStream output = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[1024 * 1024];
                    for (int count; (count = input.read(buffer)) != -1;) {
                        checkCancelled(cancelled);
                        output.write(buffer, 0, count);
                        copiedBytes += count;
                        if (copiedBytes - lastReportedBytes >= 8L * 1024 * 1024) {
                            progress.accept(new Progress(done, planned.size(), unchanged, copiedBytes, totalBytes));
                            lastReportedBytes = copiedBytes;
                        }
                    }
                    scanStatus.accept("Aguardando o USB confirmar a gravação de " + item.source().getFileName() + "...");
                }
                checkCancelled(cancelled);
                Files.setLastModifiedTime(temporary, item.modified());
                try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            progress.accept(new Progress(++done, planned.size(), unchanged, copiedBytes, totalBytes));
        }
    }

    private static void checkTarget(Path target, Path destination) throws IOException {
        for (Path parent = target.getParent(); parent != null && parent.startsWith(destination); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Destino contém link simbólico: " + parent);
        }
        if (Files.isSymbolicLink(target)) throw new IOException("Destino contém link simbólico: " + target);
        if (Files.exists(target) && !Files.isRegularFile(target)) throw new IOException("Destino não é arquivo: " + target);
    }

    private static boolean sameContent(Path first, Path second, BooleanSupplier cancelled) throws IOException {
        try (InputStream source = Files.newInputStream(first); InputStream target = Files.newInputStream(second)) {
            byte[] left = new byte[1024 * 1024];
            byte[] right = new byte[left.length];
            while (true) {
                checkCancelled(cancelled);
                int count = source.readNBytes(left, 0, left.length);
                int other = target.readNBytes(right, 0, right.length);
                if (count != other || Arrays.mismatch(left, 0, count, right, 0, other) >= 0) return false;
                if (count == 0) return true;
            }
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws Cancelled {
        if (cancelled.getAsBoolean()) throw new Cancelled();
    }
}
