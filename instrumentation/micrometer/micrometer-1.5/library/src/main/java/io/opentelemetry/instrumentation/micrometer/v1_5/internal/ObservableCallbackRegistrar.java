/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5.internal;

import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ObservableCallbackRegistrar {

  <T, H extends AutoCloseable> Registration<H> register(
      Consumer<T> callback,
      @Nullable ClassLoader callbackClassLoader,
      Function<Consumer<T>, H> buildWithCallback);

  static ObservableCallbackRegistrar direct() {
    return Direct.INSTANCE;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  final class Registration<H extends AutoCloseable> {

    private final H instrument;
    private final Runnable release;

    public Registration(H instrument, Runnable release) {
      this.instrument = instrument;
      this.release = release;
    }

    public H instrument() {
      return instrument;
    }

    public void close(Consumer<H> closeInstrument) {
      try {
        closeInstrument.accept(instrument);
      } finally {
        release.run();
      }
    }

    public void close() throws Exception {
      try {
        instrument.close();
      } finally {
        release.run();
      }
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  final class Direct implements ObservableCallbackRegistrar {

    private static final Direct INSTANCE = new Direct();

    @Override
    public <T, H extends AutoCloseable> Registration<H> register(
        Consumer<T> callback,
        @Nullable ClassLoader callbackClassLoader,
        Function<Consumer<T>, H> buildWithCallback) {
      return new Registration<>(buildWithCallback.apply(callback), () -> {});
    }

    private Direct() {}
  }
}
