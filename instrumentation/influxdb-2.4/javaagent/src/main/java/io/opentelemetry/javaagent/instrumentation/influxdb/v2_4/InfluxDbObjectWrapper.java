/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public class InfluxDbObjectWrapper {

  @Nullable
  public static <T> Consumer<T> wrapConsumer(
      @Nullable Consumer<T> callback, Context parentContext) {
    if (callback == null) {
      return null;
    }
    return value -> {
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.accept(value);
      }
    };
  }

  @Nullable
  public static <T, U> BiConsumer<T, U> wrapBiConsumer(
      @Nullable BiConsumer<T, U> callback, Context parentContext) {
    if (callback == null) {
      return null;
    }
    return (value1, value2) -> {
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.accept(value1, value2);
      }
    };
  }

  @Nullable
  public static Runnable wrapRunnable(@Nullable Runnable callback, Context parentContext) {
    if (callback == null) {
      return null;
    }
    return () -> {
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.run();
      }
    };
  }

  private InfluxDbObjectWrapper() {}
}
