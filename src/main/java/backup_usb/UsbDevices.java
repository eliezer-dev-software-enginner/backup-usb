package backup_usb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UsbDevices {
    private UsbDevices() {}

    public static List<Path> mounted() throws IOException {
        Process process = new ProcessBuilder("lsblk", "-nrpo", "NAME,MOUNTPOINT").start();
        List<Path> mounts = new ArrayList<>();
        try (var reader = process.inputReader()) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] columns = line.trim().split("\\s+", 2);
                if (columns.length == 2 && isUsb(columns[0])) {
                    Path path = Path.of(columns[1]);
                    if (Files.isDirectory(path)) mounts.add(path.toRealPath());
                }
            }
        }
        try { if (process.waitFor() != 0) throw new IOException("Falha ao consultar dispositivos USB."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        return mounts;
    }

    private static boolean isUsb(String device) throws IOException {
        Process process = new ProcessBuilder("lsblk", "-nrpo", "TRAN", "-s", device).start();
        boolean usb;
        try (var reader = process.inputReader()) { usb = reader.lines().anyMatch(line -> line.trim().equals("usb")); }
        try { if (process.waitFor() != 0) throw new IOException("Falha ao consultar transporte do dispositivo."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        return usb;
    }
}
