/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaagentHttpServerInstrumenters {

  private JavaagentHttpServerInstrumenters() {}

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapGetter<REQUEST> headerGetter) {
    return create(instrumentationName, httpAttributesGetter, headerGetter, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return create(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapGetter<REQUEST> headerGetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return create(
        DefaultHttpServerInstrumenterBuilder.create(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, headerGetter),
        instrumenterBuilderConsumer);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer) {
    return builder
        .configure(AgentCommonConfig.get())
        .setBuilderCustomizer(builderCustomizer)
        .build();
  }
}
