/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaagentHttpClientInstrumenters {

  private JavaagentHttpClientInstrumenters() {}

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return create(instrumentationName, httpAttributesGetter, null);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapSetter<REQUEST> headerSetter) {
    return create(instrumentationName, httpAttributesGetter, headerSetter, b -> {});
  }

  // this is where an HttpClientTelemetryBuilder interface would be nice
  // instead of having to pass Object and using reflection to unwrap the underlying builder
  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return create(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapSetter<REQUEST> headerSetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> defaultHttpClientTelemetryBuilder =
        new DefaultHttpClientInstrumenterBuilder<>(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter);
    if (headerSetter != null) {
      defaultHttpClientTelemetryBuilder.setHeaderSetter(headerSetter);
    }
    return create(defaultHttpClientTelemetryBuilder, instrumenterBuilderConsumer);
  }

  private static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer) {
    CommonConfig config = CommonConfig.get();
    set(config::getKnownHttpRequestMethods, builder::setKnownMethods);
    set(config::getClientRequestHeaders, builder::setCapturedRequestHeaders);
    set(config::getClientResponseHeaders, builder::setCapturedResponseHeaders);
    // is not exposed in the public API
    set(config::getPeerServiceResolver, builder::setPeerServiceResolver);
    set(
        config::shouldEmitExperimentalHttpClientTelemetry,
        builder::setEmitExperimentalHttpClientMetrics);
    // is not exposed in the public API
    builder.setBuilderCustomizer(builderCustomizer);
    return builder.build();
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}
