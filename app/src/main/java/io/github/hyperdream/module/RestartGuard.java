package io.github.hyperdream.module;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared root-action gate with lifecycle-scoped observers; no Android or IPC dependency. */
final class RestartGuard {
    static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final CopyOnWriteArrayList<Runnable> OBSERVERS = new CopyOnWriteArrayList<>();
    private RestartGuard() {}

    static boolean begin() {
        if (!RUNNING.compareAndSet(false, true)) { return false; }
        notifyState(); return true;
    }

    static void release() { RUNNING.set(false); notifyState(); }
    static void observe(Runnable observer) { OBSERVERS.addIfAbsent(observer); observer.run(); }
    static void removeObserver(Runnable observer) { OBSERVERS.remove(observer); }
    private static void notifyState() {
        for (Runnable observer : OBSERVERS) {
            try { observer.run(); } catch (RuntimeException ignored) { /* UI cannot break root cleanup. */ }
        }
    }
}
