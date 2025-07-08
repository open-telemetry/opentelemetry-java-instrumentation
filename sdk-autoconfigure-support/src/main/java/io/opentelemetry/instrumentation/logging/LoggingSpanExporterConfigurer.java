package io.opentelemetry.instrumentation.logging;

import static java.util.Collections.emptyList;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class LoggingSpanExporterConfigurer {

  private LoggingSpanExporterConfigurer() {}

  public static void enableLoggingExporter(
      SdkTracerProviderBuilder builder, ConfigProperties config) {
    // don't install another instance if the user has already explicitly requested it.
    if (loggingExporterIsNotAlreadyConfigured(config)) {
      builder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured(ConfigProperties config) {
    return !config.getList("otel.traces.exporter", emptyList()).contains("logging");
  }
}
