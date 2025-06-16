/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// todo should be inherited from testing-common - not sure why it is not
public class TestBridgeSpanExporterComponentProvider implements ComponentProvider<SpanExporter> {

  private static final Logger logger =
      LoggerFactory.getLogger(TestBridgeSpanExporterComponentProvider.class);

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
    logger.info(
        "Setting TestSpanExporterComponentProvider span exporter to {}",
        spanExporter.getClass().getName());
    TestBridgeSpanExporterComponentProvider.spanExporter = spanExporter;
  }
}
