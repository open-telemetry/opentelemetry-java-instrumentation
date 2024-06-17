/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.internal.instrumenter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.incubator.builder.AbstractHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.internal.config.CoreCommonConfig;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientInstrumenterBuilder {
  private HttpClientInstrumenterBuilder() {}

  @CanIgnoreReturnValue
  public static <T extends AbstractHttpClientTelemetryBuilder<?, ?, ?>> T configure(
      CoreCommonConfig config, T builder) {
    set(config::getKnownHttpRequestMethods, builder::setKnownMethods);
    set(config::getClientRequestHeaders, builder::setCapturedRequestHeaders);
    set(config::getClientResponseHeaders, builder::setCapturedResponseHeaders);
    set(config::getPeerServiceResolver, builder::setPeerServiceResolver);
    set(
        config::shouldEmitExperimentalHttpClientTelemetry,
        builder::setEmitExperimentalHttpClientMetrics);
    return builder;
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}
