/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.instrumentation.micrometer.v1_5.internal.ObservableCallbackRegistrar;
import io.opentelemetry.javaagent.bootstrap.WeakRefConsumer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

final class MicrometerCallbackRegistrar implements ObservableCallbackRegistrar {

  static final MicrometerCallbackRegistrar INSTANCE = new MicrometerCallbackRegistrar();

  private static final String CALLBACK_ANCHOR = MicrometerCallbackAnchor.class.getName();

  @Override
  // The injected helper returns a weak wrapper around the same Consumer<T> passed to anchor().
  @SuppressWarnings("unchecked")
  public <T, H extends AutoCloseable> Registration<H> register(
      Consumer<T> callback,
      @Nullable ClassLoader callbackClassLoader,
      Function<Consumer<T>, H> buildWithCallback) {
    if (callbackClassLoader == null) {
      return ObservableCallbackRegistrar.direct().register(callback, null, buildWithCallback);
    }

    Class<?> callbackAnchorClass;
    try {
      callbackAnchorClass = Class.forName(CALLBACK_ANCHOR, true, callbackClassLoader);
    } catch (ClassNotFoundException e) {
      return ObservableCallbackRegistrar.direct()
          .register(callback, callbackClassLoader, buildWithCallback);
    }
    if (callbackAnchorClass.getClassLoader() != callbackClassLoader) {
      return ObservableCallbackRegistrar.direct()
          .register(callback, callbackClassLoader, buildWithCallback);
    }

    WeakRefConsumer<T> weakCallback;
    try {
      Method anchorMethod = callbackAnchorClass.getMethod("anchor", Consumer.class);
      callbackAnchorClass.getMethod("release", Consumer.class);
      weakCallback = (WeakRefConsumer<T>) anchorMethod.invoke(null, callback);
    } catch (ReflectiveOperationException e) {
      return ObservableCallbackRegistrar.direct()
          .register(callback, callbackClassLoader, buildWithCallback);
    }

    MicrometerCallbackAnchorToken anchor =
        new MicrometerCallbackAnchorToken(callbackClassLoader, callback);
    try {
      H instrument = buildWithCallback.apply(weakCallback);
      weakCallback.closeWhenCollected(instrument);
      return new Registration<>(instrument, anchor::release);
    } catch (RuntimeException | Error e) {
      anchor.release();
      throw e;
    }
  }

  private static final class MicrometerCallbackAnchorToken {

    private final WeakReference<ClassLoader> callbackClassLoader;
    private final WeakReference<Consumer<?>> callback;
    private final AtomicBoolean released = new AtomicBoolean();

    private MicrometerCallbackAnchorToken(ClassLoader callbackClassLoader, Consumer<?> callback) {
      this.callbackClassLoader = new WeakReference<>(callbackClassLoader);
      this.callback = new WeakReference<>(callback);
    }

    private void release() {
      if (!released.compareAndSet(false, true)) {
        return;
      }
      ClassLoader classLoader = callbackClassLoader.get();
      Consumer<?> callback = this.callback.get();
      if (classLoader == null || callback == null) {
        return;
      }
      try {
        Class<?> callbackAnchorClass = Class.forName(CALLBACK_ANCHOR, true, classLoader);
        if (callbackAnchorClass.getClassLoader() != classLoader) {
          return;
        }
        Method releaseMethod = callbackAnchorClass.getMethod("release", Consumer.class);
        releaseMethod.invoke(null, callback);
      } catch (ReflectiveOperationException ignored) {
        // The helper is defined in the callback's class loader and should remain available.
      }
    }
  }

  private MicrometerCallbackRegistrar() {}
}
