/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.instrumentation.micrometer.v1_5.internal.ObservableCallbackRegistrar;
import io.opentelemetry.javaagent.bootstrap.WeakRefConsumer;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

final class MicrometerCallbackRegistrar implements ObservableCallbackRegistrar {

  static final MicrometerCallbackRegistrar INSTANCE = new MicrometerCallbackRegistrar();

  private static final ClassValue<Set<Consumer<?>>> callbacks =
      new ClassValue<Set<Consumer<?>>>() {
        @Override
        protected Set<Consumer<?>> computeValue(Class<?> type) {
          return Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        }
      };

  @Override
  public <T, H extends AutoCloseable> Registration<H> register(
      Consumer<T> callback, Class<?> ownerClass, Function<Consumer<T>, H> buildWithCallback) {
    callbacks.get(ownerClass).add(callback);

    WeakRefConsumer<T> weakCallback = new WeakRefConsumer<>(new WeakReference<>(callback));
    CallbackToken token = new CallbackToken(ownerClass, callback);
    try {
      H instrument = buildWithCallback.apply(weakCallback);
      weakCallback.closeWhenCollected(instrument);
      return new Registration<>(instrument, token::release);
    } catch (RuntimeException | Error e) {
      token.release();
      throw e;
    }
  }

  private static final class CallbackToken {

    private final WeakReference<Class<?>> ownerClass;
    private final WeakReference<Consumer<?>> callback;

    private CallbackToken(Class<?> ownerClass, Consumer<?> callback) {
      this.ownerClass = new WeakReference<>(ownerClass);
      this.callback = new WeakReference<>(callback);
    }

    private void release() {
      Class<?> ownerClass = this.ownerClass.get();
      Consumer<?> callback = this.callback.get();
      if (ownerClass != null && callback != null) {
        callbacks.get(ownerClass).remove(callback);
      }
    }
  }

  private MicrometerCallbackRegistrar() {}
}
