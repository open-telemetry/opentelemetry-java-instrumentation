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
  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(Object builder) {
    return create(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      TextMapSetter<REQUEST> headerSetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> DefaultHttpClientInstrumenterBuilder =
        new DefaultHttpClientInstrumenterBuilder<>(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter);
    if (headerSetter != null) {
      DefaultHttpClientInstrumenterBuilder.setHeaderSetter(headerSetter);
    }
    return create(DefaultHttpClientInstrumenterBuilder, instrumenterBuilderConsumer);
  }

  private static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      Object builder, Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer) {
    DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE> defaultBuilder =
        DefaultHttpClientInstrumenterBuilder.unwrapAndConfigure(CommonConfig.get(), builder);
    defaultBuilder.setBuilderCustomizer(builderCustomizer);
    return defaultBuilder.build();
  }
}
