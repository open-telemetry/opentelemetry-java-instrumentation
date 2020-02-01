package io.opentelemetry.auto.tooling.exporter;

import io.opentelemetry.sdk.trace.export.SpanExporter;

public interface SpanExporterFactory {
  SpanExporter newExporter() throws ExporterConfigException;
}
