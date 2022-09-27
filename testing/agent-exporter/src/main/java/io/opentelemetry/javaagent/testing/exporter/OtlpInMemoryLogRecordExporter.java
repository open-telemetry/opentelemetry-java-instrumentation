/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static java.util.logging.Level.INFO;

import io.opentelemetry.exporter.internal.otlp.logs.LogsRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

class OtlpInMemoryLogRecordExporter implements LogRecordExporter {

  private static final Logger logger =
      Logger.getLogger(OtlpInMemoryLogRecordExporter.class.getName());

  private final Queue<byte[]> collectedRequests = new ConcurrentLinkedQueue<>();

  List<byte[]> getCollectedExportRequests() {
    return new ArrayList<>(collectedRequests);
  }

  void reset() {
    collectedRequests.clear();
  }

  @Override
  public CompletableResultCode export(Collection<LogRecordData> logRecords) {
    for (LogRecordData logRecord : logRecords) {
      logger.log(INFO, "Exporting log {0}", logRecord);
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      LogsRequestMarshaler.create(logRecords).writeBinaryTo(bos);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    collectedRequests.add(bos.toByteArray());
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    reset();
    return CompletableResultCode.ofSuccess();
  }
}
