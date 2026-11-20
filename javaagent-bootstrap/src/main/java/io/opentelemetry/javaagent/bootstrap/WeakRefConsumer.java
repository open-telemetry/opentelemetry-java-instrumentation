/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class WeakRefConsumer<T> implements Consumer<T> {

  private final WeakReference<Consumer<T>> ref;
  private final AtomicReference<AutoCloseable> closeOnCollect = new AtomicReference<>();

  public WeakRefConsumer(WeakReference<Consumer<T>> ref) {
    this.ref = ref;
  }

  // Not a constructor parameter because of a chicken-and-egg problem: the SDK builder needs
  // this consumer in order to create the instrument, but the instrument is what we want to close.
  public void closeWhenCollected(AutoCloseable closeOnCollect) {
    this.closeOnCollect.set(closeOnCollect);
  }

  @Override
  public void accept(T t) {
    Consumer<T> consumer = ref.get();
    if (consumer != null) {
      consumer.accept(t);
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
