package io.opentelemetry.auto.exporters.dummyexporter;

import io.opentelemetry.auto.exportersupport.ConfigProvider;
import io.opentelemetry.auto.exportersupport.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class DummySpanExporterFactory implements SpanExporterFactory {
  @Override
  public SpanExporter fromConfig(final ConfigProvider config) {
    return new DummyExporter(config.getString("dummy.prefix", "no-prefix"));
  }
}
