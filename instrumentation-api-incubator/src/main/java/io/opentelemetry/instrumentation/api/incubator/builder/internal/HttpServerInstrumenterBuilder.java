/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreCommonConfig;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerInstrumenterBuilder {
  private HttpServerInstrumenterBuilder() {}

  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE> DefaultHttpServerTelemetryBuilder<REQUEST, RESPONSE> configure(
      CoreCommonConfig config, Object builder) {
    DefaultHttpServerTelemetryBuilder<REQUEST, RESPONSE> defaultBuilder = unwrapBuilder(builder);
    set(config::getKnownHttpRequestMethods, defaultBuilder::setKnownMethods);
    set(config::getServerRequestHeaders, defaultBuilder::setCapturedRequestHeaders);
    set(config::getServerResponseHeaders, defaultBuilder::setCapturedResponseHeaders);
    set(
        config::shouldEmitExperimentalHttpServerTelemetry,
        defaultBuilder::setEmitExperimentalHttpServerMetrics);
    return defaultBuilder;
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }

  /**
   * This method is used to access the builder field of the builder object.
   *
   * <p>This approach allows us to re-use the existing builder classes from the library modules
   */
  @SuppressWarnings("unchecked")
  private static <REQUEST, RESPONSE>
      DefaultHttpServerTelemetryBuilder<REQUEST, RESPONSE> unwrapBuilder(Object builder) {
    if (builder instanceof DefaultHttpServerTelemetryBuilder<?, ?>) {
      return (DefaultHttpServerTelemetryBuilder<REQUEST, RESPONSE>) builder;
    }
    try {
      Field field = builder.getClass().getDeclaredField("serverBuilder");
      field.setAccessible(true);
      return (DefaultHttpServerTelemetryBuilder<REQUEST, RESPONSE>) field.get(builder);
    } catch (Exception e) {
      throw new IllegalStateException("Could not access builder field", e);
    }
  }
}
