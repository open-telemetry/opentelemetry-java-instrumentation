/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestBridgeLogRecordExporterComponentProvider
    implements ComponentProvider<LogRecordExporter> {

  private static final Logger logger =
      Logger.getLogger(TestBridgeLogRecordExporterComponentProvider.class.getName());

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
    logger.log(
        Level.INFO, "Setting logRecord exporter to {0}", logRecordExporter.getClass().getName());
    TestBridgeLogRecordExporterComponentProvider.logRecordExporter = logRecordExporter;
  }
}
