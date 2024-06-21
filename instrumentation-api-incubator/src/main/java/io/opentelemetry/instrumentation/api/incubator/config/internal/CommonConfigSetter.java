/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfigSetter {
  private CommonConfigSetter() {}

  public static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}
