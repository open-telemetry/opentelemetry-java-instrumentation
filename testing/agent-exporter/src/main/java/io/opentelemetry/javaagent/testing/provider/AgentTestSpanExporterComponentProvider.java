/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Objects;

@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class AgentTestSpanExporterComponentProvider implements ComponentProvider<SpanExporter> {

  private static SpanExporter spanExporter;

  @Override
  public Class<SpanExporter> getType() {
    return SpanExporter.class;
  }

  @Override
  public String getName() {
    return "agent_test";
  }

  @Override
  public SpanExporter create(DeclarativeConfigProperties config) {
    return Objects.requireNonNull(spanExporter, "spanExporter must not be null");
  }

  public static void setSpanExporter(SpanExporter spanExporter) {
    AgentTestSpanExporterComponentProvider.spanExporter = spanExporter;
  }
}
