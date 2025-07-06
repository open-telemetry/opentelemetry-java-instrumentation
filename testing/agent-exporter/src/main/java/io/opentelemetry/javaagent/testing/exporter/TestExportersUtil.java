package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.javaagent.testing.provider.TestLogRecordExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.TestMetricExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.TestSpanExporterComponentProvider;

class TestExportersUtil {
  private TestExportersUtil() {}

  static void initTestExporters() {
    TestSpanExporterComponentProvider.setSpanExporter(AgentTestingExporterFactory.spanExporter);
    TestMetricExporterComponentProvider.setMetricExporter(
        AgentTestingExporterFactory.metricExporter);
    TestLogRecordExporterComponentProvider.setLogRecordExporter(
        AgentTestingExporterFactory.logExporter);
  }
}
