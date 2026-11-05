package backup_usb;

import backup_usb.data.InputSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import megalodonte.base.UI;
import megalodonte.base.state.State;
import megalodonte.components.FilePicker;
import megalodonte.v2.ListState;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackupViewModel {
    private static final Logger LOG = Logger.getLogger(BackupViewModel.class.getName());
    private static final Path SETTINGS = Path.of(System.getProperty("user.home"), ".config", "backup-usb", "settings.properties");
    public static final ListState<InputSource> sourcesInputIdx = ListState.ofEmpty();
    public static final ListState<String> sources = ListState.ofEmpty();
    public static int sourcesIndex = 0;
    public final State<String> source = State.of("");
    public final State<String> destination = State.of("");
    public final State<String> start = State.of("00:00");
    public final State<String> end = State.of("23:59");
    public final State<String> interval = State.of("60");
    public final State<String> devices = State.of("Buscando dispositivos...");
    public final State<String> status = State.of("Pronto.");
    public final State<String> copyStatus = State.of("Nenhuma cópia iniciada.");
    public final State<Integer> copyPercent = State.of(0);
    public final State<String> copyPercentLabel = State.of("0%");
    public final State<String> copyActionLabel = State.of("Iniciar cópia agora");
    public final State<String> watchStatus = State.of("Monitoramento ainda não configurado.");
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "backup-usb-scheduler"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean copying = new AtomicBoolean();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final AtomicBoolean resyncRequested = new AtomicBoolean();
    private final AtomicLong watchGeneration = new AtomicLong();
    private volatile SourceWatcher watcher;
    private ScheduledFuture<?> pendingWatchSync;
    private volatile Settings saved;
    private volatile long lastSlot = -1;

    private ObjectMapper objectMapper = new ObjectMapper();

    public BackupViewModel() {
        load();
        refreshDevices();
        timer.scheduleAtFixedRate(this::tick, 0, 30, TimeUnit.SECONDS);
    }

    public void chooseSource(State<String> sourceInputState) { FilePicker.chooseDirectory(null, "Escolher pasta de origem").ifPresent(path -> sourceInputState.set(path.toString())); }
    public void chooseDestination() { FilePicker.chooseDirectory(null, "Escolher pasta no USB").ifPresent(path -> destination.set(path.toString())); }

    public void refreshDevices() {
        Thread.startVirtualThread(() -> {
            try {
                List<Path> mounts = UsbDevices.mounted();
                UI.runOnUi(() -> devices.set(mounts.isEmpty() ? "Nenhum USB montado." : String.join(" • ", mounts.stream().map(Path::toString).toList())));
            } catch (IOException e) { UI.runOnUi(() -> devices.set("Não foi possível consultar os USBs: " + e.getMessage())); }
        });
    }

    public void addSourceInput(){
        sourcesIndex++;
        sourcesInputIdx.add(new InputSource(sourcesIndex,new State<>("")));
    }

    public void save() {
        try {
            Settings settings = parse();
            Properties properties = new Properties();
            properties.setProperty("source", settings.source().toString());
            properties.setProperty("sources", objectMapper.writeValueAsString(settings.sources().stream().map(Path::toString).toList()));
            properties.setProperty("destination", settings.destination().toString());
            properties.setProperty("start", settings.start().toString());
            properties.setProperty("end", settings.end().toString());
            properties.setProperty("interval", Integer.toString(settings.interval()));
            Files.createDirectories(SETTINGS.getParent());
            try (var out = Files.newOutputStream(SETTINGS)) { properties.store(out, "Backup USB"); }
            saved = settings;
            startWatching(settings);
            status.set("Definições salvas. Sincronização ativa na janela enquanto o aplicativo estiver aberto.");
        } catch (Exception e) { status.set("Não foi possível salvar: " + e.getMessage()); }
    }

    public void toggleCopy() {
        if (copying.get()) {
            cancelRequested.set(true);
            copyStatus.set("Interrompendo cópia...");
            LOG.info("Interrupção da cópia solicitada.");
            return;
        }
        try { runCopy(parse()); }
        catch (Exception e) { status.set("Configuração inválida: " + e.getMessage()); }
    }

    private void runCopy(Settings settings) {
        if (!copying.compareAndSet(false, true)) { resyncRequested.set(true); return; }
        cancelRequested.set(false);
        UI.runOnUi(() -> { copyPercent.set(0); copyPercentLabel.set("0%"); copyActionLabel.set("Interromper cópia"); copyStatus.set("Validando espaço e iniciando cópia..."); });

        LOG.info("Iniciando sincronização incremental para " + settings.destination());
        Thread.startVirtualThread(() -> {
            try {
                BackupService.Progress[] latestProgress = new BackupService.Progress[1];
                Path mount = UsbDevices.mounted().stream()
                        .filter(path -> settings.destination().startsWith(path))
                        .max((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()))
                        .orElseThrow(() -> new IOException("Destino não está em um USB montado."));
                BackupService.copy(settings.source(), settings.destination(), mount, progress -> {
                    latestProgress[0] = progress;
                    String message = progress.message();
                    LOG.info(message);
                    UI.runOnUi(() -> { copyPercent.set(progress.percent()); copyPercentLabel.set(progress.percent() + "%"); copyStatus.set(message); });
                }, cancelRequested::get, message -> {
                    LOG.info(message);
                    UI.runOnUi(() -> copyStatus.set(message));
                });
                String result = latestProgress[0].total() == 0 ? latestProgress[0].message()
                        : "Sincronização concluída. " + latestProgress[0].message();
                UI.runOnUi(() -> { copyPercent.set(100); copyPercentLabel.set("100%"); copyStatus.set(result); });
                LOG.info(result + " Destino: " + settings.destination());
            } catch (BackupService.Cancelled e) {
                LOG.info("Cópia interrompida pelo usuário.");
                UI.runOnUi(() -> copyStatus.set("Cópia interrompida pelo usuário."));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Falha na cópia", e);
                UI.runOnUi(() -> copyStatus.set("Falha na cópia: " + e.getMessage()));
            } finally {
                copying.set(false);
                UI.runOnUi(() -> copyActionLabel.set("Iniciar cópia agora"));
                if (resyncRequested.getAndSet(false) && !cancelRequested.get()) {
                    Settings latest = saved;
                    if (latest != null && BackupSchedule.inWindow(latest.start(), latest.end(), LocalTime.now())) runCopy(latest);
                }
            }
        });

        settings.sources.forEach(source->{
            LOG.info("Iniciando sincronização incremental para " + settings.destination());
            Thread.startVirtualThread(() -> {
                try {
                    BackupService.Progress[] latestProgress = new BackupService.Progress[1];
                    Path mount = UsbDevices.mounted().stream()
                            .filter(path -> settings.destination().startsWith(path))
                            .max((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()))
                            .orElseThrow(() -> new IOException("Destino não está em um USB montado."));
                    BackupService.copy(source, settings.destination(), mount, progress -> {
                        latestProgress[0] = progress;
                        String message = progress.message();
                        LOG.info(message);
                        UI.runOnUi(() -> { copyPercent.set(progress.percent()); copyPercentLabel.set(progress.percent() + "%"); copyStatus.set(message); });
                    }, cancelRequested::get, message -> {
                        LOG.info(message);
                        UI.runOnUi(() -> copyStatus.set(message));
                    });
                    String result = latestProgress[0].total() == 0 ? latestProgress[0].message()
                            : "Sincronização concluída. " + latestProgress[0].message();
                    UI.runOnUi(() -> { copyPercent.set(100); copyPercentLabel.set("100%"); copyStatus.set(result); });
                    LOG.info(result + " Destino: " + settings.destination());
                } catch (BackupService.Cancelled e) {
                    LOG.info("Cópia interrompida pelo usuário.");
                    UI.runOnUi(() -> copyStatus.set("Cópia interrompida pelo usuário."));
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Falha na cópia", e);
                    UI.runOnUi(() -> copyStatus.set("Falha na cópia: " + e.getMessage()));
                } finally {
                    copying.set(false);
                    UI.runOnUi(() -> copyActionLabel.set("Iniciar cópia agora"));
                    if (resyncRequested.getAndSet(false) && !cancelRequested.get()) {
                        Settings latest = saved;
                        if (latest != null && BackupSchedule.inWindow(latest.start(), latest.end(), LocalTime.now())) runCopy(latest);
                    }
                }
            });
        });
    }

    private void startWatching(Settings settings) {
        long generation = watchGeneration.incrementAndGet();
        SourceWatcher previous = watcher;
        watcher = null;
        if (previous != null) try { previous.close(); } catch (IOException e) { LOG.log(Level.WARNING, "Falha ao fechar monitor anterior", e); }
        watchStatus.set("Preparando monitoramento da origem...");
        Thread.startVirtualThread(() -> {
            settings.sources().forEach(source->{
                try {
                    SourceWatcher next = SourceWatcher.start(source, () -> sourceChanged(settings),
                            error -> {
                                if (watchGeneration.get() != generation) return;
                                LOG.log(Level.WARNING, "Monitoramento da origem falhou", error);
                                UI.runOnUi(() -> { if (watchGeneration.get() == generation) watchStatus.set("Monitoramento falhou: " + error.getMessage()); });
                            });
                    if (watchGeneration.get() != generation) { next.close(); return; }
                    watcher = next;
                    UI.runOnUi(() -> { if (watchGeneration.get() == generation) watchStatus.set("Origem monitorada durante a janela diária; reconciliação a cada " + settings.interval() + " min."); });
                } catch (Exception e) {
                    if (watchGeneration.get() != generation) return;
                    LOG.log(Level.WARNING, "Não foi possível monitorar a origem", e);
                    UI.runOnUi(() -> { if (watchGeneration.get() == generation) watchStatus.set("Monitoramento indisponível; reconciliação periódica permanece ativa: " + e.getMessage()); });
                }
            });

        });
    }

    private synchronized void sourceChanged(Settings settings) {
        if (pendingWatchSync != null) pendingWatchSync.cancel(false);
        pendingWatchSync = timer.schedule(() -> {
            if (settings.equals(saved) && BackupSchedule.inWindow(settings.start(), settings.end(), LocalTime.now())) runCopy(settings);
        }, 2, TimeUnit.SECONDS);
    }

    public void close() {
        watchGeneration.incrementAndGet();
        SourceWatcher current = watcher;
        if (current != null) try { current.close(); } catch (IOException e) { LOG.log(Level.WARNING, "Falha ao fechar monitor", e); }
        timer.shutdownNow();
        cancelRequested.set(true);
    }

    private void tick() {
        Settings settings = saved;
        if (settings == null) return;
        LocalTime now = LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        if (!BackupSchedule.due(settings.start(), settings.end(), settings.interval(), now)) return;
        long slot = java.time.LocalDate.now().toEpochDay() * 1440 + minute;
        if (lastSlot != slot) { lastSlot = slot; runCopy(settings); }
    }

    private Settings parse() {
        if (source.get().isBlank() || destination.get().isBlank()) throw new IllegalArgumentException("Informe origem e destino.");
        Path src = Path.of(source.get()).toAbsolutePath().normalize();
        List<Path> sources = sourcesInputIdx.get().stream().map(s -> Path.of(s.source().get()).toAbsolutePath().normalize()).toList();
        Path dst = Path.of(destination.get()).toAbsolutePath().normalize();
        LocalTime from = LocalTime.parse(start.get());
        LocalTime to = LocalTime.parse(end.get());
        int minutes = Integer.parseInt(interval.get());
        if (to.isBefore(from) || minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Janela ou intervalo inválido.");
        return new Settings(src,sources, dst, from, to, minutes);
    }

    private void load() {
        if (!Files.exists(SETTINGS)) return;
        try {

            Properties properties = new Properties();
            try (var in = Files.newInputStream(SETTINGS)) { properties.load(in); }
            source.set(properties.getProperty("source", ""));
            sourcesInputIdx.clear();
            sourcesIndex = 0;
            for (Object stored : objectMapper.readValue(properties.getProperty("sources", "[]"), List.class)) {
                sourcesInputIdx.add(new InputSource(++sourcesIndex, State.of(stored.toString())));
            }
            destination.set(properties.getProperty("destination", ""));
            start.set(properties.getProperty("start", "00:00"));
            end.set(properties.getProperty("end", "23:59"));
            interval.set(properties.getProperty("interval", "60"));
            saved = parse();
            startWatching(saved);
        } catch (Exception e) { status.set("Definições salvas inválidas: " + e.getMessage()); }
    }

    private record Settings(Path source, List<Path> sources, Path destination, LocalTime start, LocalTime end, int interval) {}
}
