package io.opentelemetry.instrumentation.httpserver.internal;

import com.sun.net.httpserver.HttpExchange;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdkInstrumenterBuilderFactory {
  private JdkInstrumenterBuilderFactory() {}

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-server";

  public static DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange> getServerBuilder(
      OpenTelemetry openTelemetry) {
    return DefaultHttpServerInstrumenterBuilder.create(
        INSTRUMENTATION_NAME,
        openTelemetry,
        JdkHttpServerAttributesGetter.INSTANCE,
        ExchangeContextGetter.INSTANCE);
  }
}
