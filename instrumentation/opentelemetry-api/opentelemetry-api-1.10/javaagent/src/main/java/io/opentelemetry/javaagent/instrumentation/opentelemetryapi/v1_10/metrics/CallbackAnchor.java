/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.javaagent.bootstrap.WeakRefConsumer;
import io.opentelemetry.javaagent.bootstrap.WeakRefRunnable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility for preventing class loader leaks when bridging async instrument callbacks from the
 * application to the agent.
 *
 * <p>When a user creates an async instrument with a callback, the callback is passed over the
 * bridge and the agent holds it, which transitively holds the user's class loader preventing it
 * from being collected. This is a problem in app servers where apps are undeployed/re-deployed
 * without restarting the JVM.
 *
 * <p>The solution has two parts:
 *
 * <ol>
 *   <li>Anchor the bridging callback to the app class loader via a static field on this helper
 *       class (which is injected per class loader). This ties the callback's lifecycle to the class
 *       loader's lifecycle.
 *   <li>Return a {@link WeakRefConsumer}/{@link WeakRefRunnable} (defined on the bootstrap class
 *       loader) that holds only a {@link WeakReference} to the anchored callback. Since the
 *       wrapper's class is on the bootstrap class loader, the SDK holding it does not pin the app
 *       class loader. When the app is undeployed and the class loader is collected, the anchored
 *       callback is collected too, and the weak reference goes null.
 * </ol>
 */
public final class CallbackAnchor {

  // Anchors callbacks to this class's lifecycle. Since this class is injected as a helper into each
  // application class loader, callbacks are naturally tied to their class loader's lifecycle.
  private static final List<Object> callbacks = new CopyOnWriteArrayList<>();

  public static <T, R extends AutoCloseable> R anchor(
      Function<Consumer<T>, R> buildFn, Consumer<T> callback) {
    callbacks.add(callback);
    WeakRefConsumer<T> weak = new WeakRefConsumer<>(new WeakReference<>(callback));
    R instrument = buildFn.apply(weak);
    weak.closeWhenCollected(instrument);
    return instrument;
  }

  public static <R extends AutoCloseable> R anchorBatch(
      Function<Runnable, R> buildFn, Runnable callback) {
    callbacks.add(callback);
    WeakRefRunnable weak = new WeakRefRunnable(new WeakReference<>(callback));
    R instrument = buildFn.apply(weak);
    weak.closeWhenCollected(instrument);
    return instrument;
  }

  private CallbackAnchor() {}
}
