/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.javaagent.bootstrap.WeakRefConsumer;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MicrometerCallbackAnchor {

  private static final Map<IdentityKey, Integer> callbacks = new ConcurrentHashMap<>();

  public static <T> WeakRefConsumer<T> anchor(Consumer<T> callback) {
    callbacks.merge(new IdentityKey(callback), 1, Integer::sum);
    return new WeakRefConsumer<>(new WeakReference<>(callback));
  }

  public static void release(Consumer<?> callback) {
    callbacks.computeIfPresent(
        new IdentityKey(callback), (key, count) -> count == 1 ? null : count - 1);
  }

  private static final class IdentityKey {

    private final Object callback;

    private IdentityKey(Object callback) {
      this.callback = callback;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof IdentityKey && callback == ((IdentityKey) other).callback;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(callback);
    }
  }

  private MicrometerCallbackAnchor() {}
}
