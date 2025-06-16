/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestBridgeSpanExporterComponentProvider implements ComponentProvider<SpanExporter> {

  private static final Logger logger =
      Logger.getLogger(TestBridgeSpanExporterComponentProvider.class.getName());

  private static SpanExporter spanExporter;

  @Override
  public Class<SpanExporter> getType() {
    return SpanExporter.class;
  }

  @Override
  public String getName() {
    return "test_bridge";
  }

  @Override
  public SpanExporter create(DeclarativeConfigProperties config) {
    return spanExporter;
  }

  public static void setSpanExporter(SpanExporter spanExporter) {
    logger.log(Level.INFO, "Setting span exporter to {0}", spanExporter.getClass().getName());
    TestBridgeSpanExporterComponentProvider.spanExporter = spanExporter;
  }
}
