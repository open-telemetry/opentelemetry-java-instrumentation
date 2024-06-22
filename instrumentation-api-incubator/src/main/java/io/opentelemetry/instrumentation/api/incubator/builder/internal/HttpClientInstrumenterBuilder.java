/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreCommonConfig;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientInstrumenterBuilder {
  private HttpClientInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE>
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> configure(
          CoreCommonConfig config, Object builder) {
    DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> defaultBuilder = unwrapBuilder(builder);
    set(config::getKnownHttpRequestMethods, defaultBuilder::setKnownMethods);
    set(config::getClientRequestHeaders, defaultBuilder::setCapturedRequestHeaders);
    set(config::getClientResponseHeaders, defaultBuilder::setCapturedResponseHeaders);
    set(config::getPeerServiceResolver, defaultBuilder::setPeerServiceResolver);
    set(
        config::shouldEmitExperimentalHttpClientTelemetry,
        defaultBuilder::setEmitExperimentalHttpClientMetrics);
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
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> unwrapBuilder(Object builder) {
    if (builder instanceof DefaultHttpClientInstrumenterBuilder<?, ?>) {
      return (DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>) builder;
    }
    try {
      Field field = builder.getClass().getDeclaredField("builder");
      field.setAccessible(true);
      return (DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>) field.get(builder);
    } catch (Exception e) {
      throw new IllegalStateException("Could not access builder field", e);
    }
  }
}
