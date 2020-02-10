package io.opentelemetry.auto.dummyexporter;

import io.opentelemetry.auto.exportersupport.ConfigProvider;
import io.opentelemetry.auto.exportersupport.ExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class DummyExporterFactory implements ExporterFactory {
  @Override
  public SpanExporter fromConfig(final ConfigProvider config) {
    return new DummyExporter(config.getString("prefix", "no-prefix"));
  }
}
