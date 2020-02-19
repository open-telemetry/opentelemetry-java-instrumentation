package io.opentelemetry.auto.exporters.loggingexporter;

import io.opentelemetry.auto.exportersupport.ConfigProvider;
import io.opentelemetry.auto.exportersupport.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class LoggingExporterFactory implements SpanExporterFactory {
  @Override
  public SpanExporter fromConfig(final ConfigProvider config) {
    return new LoggingExporter(config.getString("dummy.prefix", "no-prefix"));
  }
}
