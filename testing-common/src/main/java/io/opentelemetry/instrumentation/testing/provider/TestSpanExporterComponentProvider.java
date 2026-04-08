/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class TestSpanExporterComponentProvider implements ComponentProvider {

  private static SpanExporter spanExporter;

  @Override
  public Class<SpanExporter> getType() {
    return SpanExporter.class;
  }

  @Override
  public String getName() {
    return "test";
  }

  @Override
  public SpanExporter create(DeclarativeConfigProperties config) {
    return requireNonNull(spanExporter, "spanExporter must not be null");
  }

  public static SpanExporter getSpanExporter() {
    return spanExporter;
  }

  public static void setSpanExporter(SpanExporter spanExporter) {
    TestSpanExporterComponentProvider.spanExporter = spanExporter;
  }
}
