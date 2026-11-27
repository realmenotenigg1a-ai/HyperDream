package io.github.hyperdream.module;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared across Activities and both explicit root actions; never queue two disruptive restarts. */
final class RestartGuard {
    static final AtomicBoolean RUNNING = new AtomicBoolean();
    private RestartGuard() {}
}
