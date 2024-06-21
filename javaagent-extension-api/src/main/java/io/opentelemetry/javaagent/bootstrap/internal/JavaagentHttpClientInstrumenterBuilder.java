/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.incubator.builder.DefaultHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaagentHttpClientInstrumenterBuilder {

  private JavaagentHttpClientInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapSetter<REQUEST>> headerSetter) {
    return createWithCustomizer(
        instrumentationName, httpAttributesGetter, headerSetter, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      HttpClientTelemetryBuilder<?, REQUEST, RESPONSE> builder) {
    return createWithCustomizer(builder, customizer -> {});
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      String instrumentationName,
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      Optional<TextMapSetter<REQUEST>> headerSetter,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    return createWithCustomizer(
        new DefaultHttpClientTelemetryBuilder<>(
            instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, headerSetter),
        instrumenterBuilderConsumer);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createWithCustomizer(
      HttpClientTelemetryBuilder<?, REQUEST, RESPONSE> builder,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {
    CommonConfig config = CommonConfig.get();
    DefaultHttpClientTelemetryBuilder<REQUEST, RESPONSE> defaultBuilder = unwrapBuilder(builder);
    set(config::getKnownHttpRequestMethods, defaultBuilder::setKnownMethods);
    set(config::getClientRequestHeaders, defaultBuilder::setCapturedRequestHeaders);
    set(config::getClientResponseHeaders, defaultBuilder::setCapturedResponseHeaders);
    // is not exposed in the public API
    set(config::getPeerServiceResolver, defaultBuilder::setPeerServiceResolver);
    set(
        config::shouldEmitExperimentalHttpClientTelemetry,
        defaultBuilder::setEmitExperimentalHttpClientMetrics);
    // is not exposed in the public API
    return defaultBuilder.instrumenter(instrumenterBuilderConsumer);
  }

  /**
   * This method is used to access the builder field of the {@link HttpClientTelemetryBuilder}
   * class. This is a workaround to access the builder field which is not exposed in the public API.
   *
   * <p>This approach allows us to re-use the existing builder classes from the library modules
   */
  @SuppressWarnings("unchecked")
  private static <REQUEST, RESPONSE>
      DefaultHttpClientTelemetryBuilder<REQUEST, RESPONSE> unwrapBuilder(
          HttpClientTelemetryBuilder<?, REQUEST, RESPONSE> builder) {
    if (builder instanceof DefaultHttpClientTelemetryBuilder<?, ?>) {
      return (DefaultHttpClientTelemetryBuilder<REQUEST, RESPONSE>) builder;
    }
    try {
      Field field = builder.getClass().getDeclaredField("builder");
      field.setAccessible(true);
      return (DefaultHttpClientTelemetryBuilder<REQUEST, RESPONSE>) field.get(builder);
    } catch (Exception e) {
      throw new IllegalStateException("Could not access builder field", e);
    }
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}
