package backup_usb;

import megalodonte.base.UI;
import megalodonte.base.state.State;
import megalodonte.components.FilePicker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackupViewModel {
    private static final Logger LOG = Logger.getLogger(BackupViewModel.class.getName());
    private static final Path SETTINGS = Path.of(System.getProperty("user.home"), ".config", "backup-usb", "settings.properties");
    public final State<String> source = State.of("");
    public final State<String> destination = State.of("");
    public final State<String> start = State.of("00:00");
    public final State<String> end = State.of("23:59");
    public final State<String> interval = State.of("60");
    public final State<String> devices = State.of("Buscando dispositivos...");
    public final State<String> status = State.of("Pronto.");
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "backup-usb-scheduler"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean copying = new AtomicBoolean();
    private volatile Settings saved;
    private volatile long lastSlot = -1;

    public BackupViewModel() {
        load();
        refreshDevices();
        timer.scheduleAtFixedRate(this::tick, 0, 30, TimeUnit.SECONDS);
    }

    public void chooseSource() { FilePicker.chooseDirectory(null, "Escolher pasta de origem").ifPresent(path -> source.set(path.toString())); }
    public void chooseDestination() { FilePicker.chooseDirectory(null, "Escolher pasta no USB").ifPresent(path -> destination.set(path.toString())); }

    public void refreshDevices() {
        Thread.startVirtualThread(() -> {
            try {
                List<Path> mounts = UsbDevices.mounted();
                UI.runOnUi(() -> devices.set(mounts.isEmpty() ? "Nenhum USB montado." : String.join(" • ", mounts.stream().map(Path::toString).toList())));
            } catch (IOException e) { UI.runOnUi(() -> devices.set("Não foi possível consultar os USBs: " + e.getMessage())); }
        });
    }

    public void save() {
        try {
            Settings settings = parse();
            Properties properties = new Properties();
            properties.setProperty("source", settings.source().toString());
            properties.setProperty("destination", settings.destination().toString());
            properties.setProperty("start", settings.start().toString());
            properties.setProperty("end", settings.end().toString());
            properties.setProperty("interval", Integer.toString(settings.interval()));
            Files.createDirectories(SETTINGS.getParent());
            try (var out = Files.newOutputStream(SETTINGS)) { properties.store(out, "Backup USB"); }
            saved = settings;
            status.set("Definições salvas. Agendamento ativo enquanto o aplicativo estiver aberto.");
        } catch (Exception e) { status.set("Não foi possível salvar: " + e.getMessage()); }
    }

    public void copyNow() {
        try { runCopy(parse()); }
        catch (Exception e) { status.set("Configuração inválida: " + e.getMessage()); }
    }

    private void runCopy(Settings settings) {
        if (!copying.compareAndSet(false, true)) { UI.runOnUi(() -> status.set("Já existe uma cópia em andamento.")); return; }
        UI.runOnUi(() -> status.set("Validando espaço e iniciando cópia..."));
        Thread.startVirtualThread(() -> {
            try {
                Path mount = UsbDevices.mounted().stream()
                        .filter(path -> settings.destination().startsWith(path))
                        .max((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()))
                        .orElseThrow(() -> new IOException("Destino não está em um USB montado."));
                BackupService.copy(settings.source(), settings.destination(), mount,
                        message -> UI.runOnUi(() -> status.set(message)));
                UI.runOnUi(() -> status.set("Cópia concluída."));
                LOG.info("Cópia concluída em " + settings.destination());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Falha na cópia", e);
                UI.runOnUi(() -> status.set("Falha na cópia: " + e.getMessage()));
            } finally { copying.set(false); }
        });
    }

    private void tick() {
        Settings settings = saved;
        if (settings == null) return;
        LocalTime now = LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        int first = settings.start().getHour() * 60 + settings.start().getMinute();
        int last = settings.end().getHour() * 60 + settings.end().getMinute();
        if (minute < first || minute > last || (minute - first) % settings.interval() != 0) return;
        long slot = java.time.LocalDate.now().toEpochDay() * 1440 + minute;
        if (lastSlot != slot) { lastSlot = slot; runCopy(settings); }
    }

    private Settings parse() {
        if (source.get().isBlank() || destination.get().isBlank()) throw new IllegalArgumentException("Informe origem e destino.");
        Path src = Path.of(source.get()).toAbsolutePath().normalize();
        Path dst = Path.of(destination.get()).toAbsolutePath().normalize();
        LocalTime from = LocalTime.parse(start.get());
        LocalTime to = LocalTime.parse(end.get());
        int minutes = Integer.parseInt(interval.get());
        if (to.isBefore(from) || minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Janela ou intervalo inválido.");
        return new Settings(src, dst, from, to, minutes);
    }

    private void load() {
        if (!Files.exists(SETTINGS)) return;
        try {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(SETTINGS)) { properties.load(in); }
            source.set(properties.getProperty("source", ""));
            destination.set(properties.getProperty("destination", ""));
            start.set(properties.getProperty("start", "00:00"));
            end.set(properties.getProperty("end", "23:59"));
            interval.set(properties.getProperty("interval", "60"));
            saved = parse();
        } catch (Exception e) { status.set("Definições salvas inválidas: " + e.getMessage()); }
    }

    private record Settings(Path source, Path destination, LocalTime start, LocalTime end, int interval) {}
}
