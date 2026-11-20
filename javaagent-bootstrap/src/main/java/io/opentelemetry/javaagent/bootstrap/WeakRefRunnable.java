/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

public final class WeakRefRunnable implements Runnable {

  private final WeakReference<Runnable> ref;
  private final WeakReference<ClassLoader> contextClassLoader;
  private final AtomicReference<AutoCloseable> closeOnCollect = new AtomicReference<>();

  public WeakRefRunnable(WeakReference<Runnable> ref) {
    this.ref = ref;
    contextClassLoader = new WeakReference<>(Thread.currentThread().getContextClassLoader());
  }

  // Not a constructor parameter because of a chicken-and-egg problem: the SDK builder needs
  // this runnable in order to create the instrument, but the instrument is what we want to close.
  public void closeWhenCollected(AutoCloseable closeable) {
    this.closeOnCollect.set(closeable);
  }

  @Override
  public void run() {
    Runnable runnable = ref.get();
    if (runnable != null) {
      Thread thread = Thread.currentThread();
      ClassLoader originalClassLoader = thread.getContextClassLoader();
      thread.setContextClassLoader(contextClassLoader.get());
      try {
        runnable.run();
      } finally {
        thread.setContextClassLoader(originalClassLoader);
      }
    } else {
      close();
    }
  }

  private void close() {
    AutoCloseable closeable = closeOnCollect.getAndSet(null);
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ignored) {
        // ignored - close() on SDK instruments should not throw
      }
    }
  }
}
