/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.export.LogExporter;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemoryLogExporter implements LogExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemoryLogExporter.class);

  private final OtlpInMemoryCollector collector;
  private final LogExporter delegate;

  OtlpInMemoryLogExporter(OtlpInMemoryCollector collector) {
    this.collector = collector;

    collector.start();

    delegate = OtlpGrpcLogExporter.builder().setEndpoint(collector.getEndpoint()).build();
  }

  @Override
  public CompletableResultCode export(Collection<LogData> logs) {
    for (LogData log : logs) {
      logger.info("Exporting log {}", log);
    }
    return delegate.export(logs);
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
