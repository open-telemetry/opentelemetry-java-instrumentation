/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.jaeger;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

@AutoService(SpanExporterFactory.class)
public class JaegerThriftExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(Properties config) {
    return JaegerThriftSpanExporter.builder().readProperties(config).build();
  }

  @Override
  public Set<String> getNames() {
    return Collections.singleton("jaeger-thrift");
  }
}
