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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaagentHttpClientInstrumenters {

  private JavaagentHttpClientInstrumenters() {}

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return create(
        DefaultHttpClientInstrumenterBuilder.create(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter),
        b -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapSetter<REQUEST> headerSetter) {
    return create(instrumentationName, httpAttributesGetter, headerSetter, b -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return create(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapSetter<REQUEST> headerSetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return create(
        DefaultHttpClientInstrumenterBuilder.create(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, headerSetter),
        instrumenterBuilderConsumer);
  }

  private static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer) {
    return builder
        .configure(AgentCommonConfig.get())
        .setBuilderCustomizer(builderCustomizer)
        .build();
  }
}
