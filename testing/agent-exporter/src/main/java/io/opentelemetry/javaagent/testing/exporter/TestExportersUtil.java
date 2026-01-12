/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.javaagent.testing.provider.AgentTestLogRecordExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.AgentTestMetricExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.AgentTestSpanExporterComponentProvider;

public class TestExportersUtil {
  private TestExportersUtil() {}

  public static void initTestExporters() {
    AgentTestSpanExporterComponentProvider.setSpanExporter(
        AgentTestingExporterFactory.spanExporter);
    AgentTestMetricExporterComponentProvider.setMetricExporter(
        AgentTestingExporterFactory.metricExporter);
    AgentTestLogRecordExporterComponentProvider.setLogRecordExporter(
        AgentTestingExporterFactory.logExporter);
  }
}
