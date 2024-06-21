/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.HttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaagentHttpServerInstrumenterBuilder {

  private JavaagentHttpServerInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapGetter<REQUEST>> headerGetter) {
    return createWithCustomizer(
        instrumentationName, httpAttributesGetter, headerGetter, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(Object builder) {
    return createWithCustomizer(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      String instrumentationName,
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapGetter<REQUEST>> headerGetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return createWithCustomizer(
        new DefaultHttpServerTelemetryBuilder<>(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, headerGetter),
        instrumenterBuilderConsumer);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      Object builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return HttpServerInstrumenterBuilder.<REQUEST, RESPONSE>configure(CommonConfig.get(), builder)
        .instrumenter(instrumenterBuilderConsumer);
  }
}
