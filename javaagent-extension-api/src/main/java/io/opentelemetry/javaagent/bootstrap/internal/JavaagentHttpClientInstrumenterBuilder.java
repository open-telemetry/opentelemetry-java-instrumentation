/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.incubator.builder.AbstractHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("rawtypes")
public class JavaagentHttpClientInstrumenterBuilder<REQUEST, RESPONSE>
    extends AbstractHttpClientTelemetryBuilder<
        JavaagentHttpClientInstrumenterBuilder, REQUEST, RESPONSE> {

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapSetter<REQUEST>> headerSetter) {
    return createWithCustomizer(
        instrumentationName, httpAttributesGetter, headerSetter, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      AbstractHttpClientTelemetryBuilder<?, REQUEST, RESPONSE> builder) {
    return createWithCustomizer(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapSetter<REQUEST>> headerSetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return createWithCustomizer(
        new JavaagentHttpClientInstrumenterBuilder<>(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, headerSetter),
        instrumenterBuilderConsumer);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      AbstractHttpClientTelemetryBuilder<?, REQUEST, RESPONSE> builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    CommonConfig config = CommonConfig.get();
    set(config::getKnownHttpRequestMethods, builder::setKnownMethods);
    set(config::getClientRequestHeaders, builder::setCapturedRequestHeaders);
    set(config::getClientResponseHeaders, builder::setCapturedResponseHeaders);
    set(config::getPeerServiceResolver, builder::setPeerServiceResolver);
    set(
        config::shouldEmitExperimentalHttpClientTelemetry,
        builder::setEmitExperimentalHttpClientMetrics);
    return builder.instrumenter(instrumenterBuilderConsumer);
  }

  private JavaagentHttpClientInstrumenterBuilder(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      Optional<TextMapSetter<REQUEST>> headerSetter) {
    super(instrumentationName, openTelemetry, attributesGetter, headerSetter);
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}
