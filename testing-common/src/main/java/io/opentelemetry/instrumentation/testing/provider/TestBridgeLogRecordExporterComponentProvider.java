/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

public class TestBridgeLogRecordExporterComponentProvider
    implements ComponentProvider<LogRecordExporter> {

  private static LogRecordExporter logRecordExporter;

  @Override
  public Class<LogRecordExporter> getType() {
    return LogRecordExporter.class;
  }

  @Override
  public String getName() {
    return "test_bridge";
  }

  @Override
  public LogRecordExporter create(DeclarativeConfigProperties config) {
    return logRecordExporter;
  }

  public static void setLogRecordExporter(LogRecordExporter logRecordExporter) {
    TestBridgeLogRecordExporterComponentProvider.logRecordExporter = logRecordExporter;
  }
}
