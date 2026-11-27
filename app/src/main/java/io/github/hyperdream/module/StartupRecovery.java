package io.github.hyperdream.module;

/** Bounded, single-threaded startup recovery. Only settings reads may be retried. */
final class StartupRecovery<T> {
    interface Scheduler { void schedule(Runnable task, long delayMillis); }
    interface Reader<T> { T read(); }
    interface Installer<T> { void install(T settings); }
    interface Reporter { void report(Stage stage, int attempt, String failure); }
    enum Stage { CONTEXT_READY, READING_SETTINGS, WAITING_FOR_SETTINGS, SETTINGS_UNAVAILABLE,
        SETTINGS_DENIED, INSTALLING, INITIALIZED, INSTALL_FAILED }

    private static final long[] RETRY_DELAYS = {1_000, 3_000, 7_000, 15_000, 30_000};
    private final Scheduler scheduler;
    private final Reader<T> reader;
    private final Installer<T> installer;
    private final Reporter reporter;
    private boolean started;
    private int attempt;

    StartupRecovery(Scheduler scheduler, Reader<T> reader, Installer<T> installer, Reporter reporter) {
        this.scheduler = scheduler;
        this.reader = reader;
        this.installer = installer;
        this.reporter = reporter;
    }

    void start() {
        if (started) { return; }
        started = true;
        report(Stage.CONTEXT_READY, "");
        read();
    }

    private void read() {
        attempt++;
        report(Stage.READING_SETTINGS, "");
        T settings;
        try {
            settings = reader.read();
        } catch (SecurityException denied) {
            // Do not repeatedly attempt a bridge that rejected our identity.
            report(Stage.SETTINGS_DENIED, denied.getClass().getSimpleName());
            return;
        } catch (RuntimeException | LinkageError failure) {
            retry(failure.getClass().getSimpleName());
            return;
        }
        if (settings == null) { retry("NullSettings"); return; }
        // Never retry partially installed hooks: doing so can double-scale animations.
        report(Stage.INSTALLING, "");
        try {
            installer.install(settings);
            report(Stage.INITIALIZED, "");
        } catch (RuntimeException | LinkageError failure) {
            report(Stage.INSTALL_FAILED, failure.getClass().getSimpleName());
        }
    }

    private void retry(String failure) {
        if (attempt > RETRY_DELAYS.length) {
            report(Stage.SETTINGS_UNAVAILABLE, failure);
            return;
        }
        report(Stage.WAITING_FOR_SETTINGS, failure);
        scheduler.schedule(this::read, RETRY_DELAYS[attempt - 1]);
    }

    private void report(Stage stage, String failure) {
        // Diagnostics must never prevent hook initialization or cause a retry.
        try { reporter.report(stage, attempt, failure); }
        catch (RuntimeException | LinkageError ignored) { /* Best effort. */ }
    }
}
