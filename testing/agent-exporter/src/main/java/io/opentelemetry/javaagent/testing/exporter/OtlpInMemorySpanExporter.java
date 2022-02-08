/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemorySpanExporter implements SpanExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemorySpanExporter.class);

  private final OtlpInMemoryCollector collector;
  private final SpanExporter delegate;

  OtlpInMemorySpanExporter(OtlpInMemoryCollector collector) {
    this.collector = collector;

    collector.start();

    delegate = OtlpGrpcSpanExporter.builder().setEndpoint(collector.getEndpoint()).build();
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    for (SpanData span : spans) {
      logger.info("Exporting span {}", span);
    }
    return delegate.export(spans);
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    collector.stop();
    return delegate.shutdown();
  }
}
