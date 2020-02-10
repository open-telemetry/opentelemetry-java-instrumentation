package io.opentelemetry.auto.exportersupport;

import io.opentelemetry.sdk.trace.export.SpanExporter;

public interface ExporterFactory {
  SpanExporter fromConfig(ConfigProvider config);
}
