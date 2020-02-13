package io.opentelemetry.auto.exportersupport;

import io.opentelemetry.sdk.trace.export.SpanExporter;

public interface SpanExporterFactory {
  SpanExporter fromConfig(ConfigProvider config);
}
