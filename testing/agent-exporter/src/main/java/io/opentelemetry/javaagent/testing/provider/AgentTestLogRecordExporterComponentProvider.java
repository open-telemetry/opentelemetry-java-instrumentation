/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

@AutoService(ComponentProvider.class)
public class AgentTestLogRecordExporterComponentProvider implements ComponentProvider {

  private static LogRecordExporter logRecordExporter;

  @Override
  public Class<LogRecordExporter> getType() {
    return LogRecordExporter.class;
  }

  @Override
  public String getName() {
    return "agent_test";
  }

  @Override
  public LogRecordExporter create(DeclarativeConfigProperties config) {
    return requireNonNull(logRecordExporter, "logRecordExporter must not be null");
  }

  public static void setLogRecordExporter(LogRecordExporter logRecordExporter) {
    AgentTestLogRecordExporterComponentProvider.logRecordExporter = logRecordExporter;
  }
}
