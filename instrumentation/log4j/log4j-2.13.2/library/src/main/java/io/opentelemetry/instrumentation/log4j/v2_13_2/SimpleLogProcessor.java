/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logging.LogProcessor;
import io.opentelemetry.sdk.logging.data.LogRecord;
import io.opentelemetry.sdk.logging.export.BatchLogProcessor;
import io.opentelemetry.sdk.logging.export.LogExporter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleLogProcessor implements LogProcessor {

  private static final Logger logger = Logger.getLogger(SimpleLogProcessor.class.getName());

  private final LogExporter logExporter;
  private final Set<CompletableResultCode> pendingExports =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  /**
   * Returns a new {@link SimpleLogProcessor} which exports spans to the {@link LogExporter}
   * synchronously.
   *
   * <p>This processor will cause all logs to be exported directly as they finish, meaning each
   * export request will have a single log. Most backends will not perform well with a single log
   * per request so unless you know what you're doing, strongly consider using {@link
   * BatchLogProcessor} instead, including in special environments such as serverless runtimes.
   * {@link SimpleLogProcessor} is generally meant to for logging exporters only.
   */
  public static LogProcessor create(LogExporter exporter) {
    requireNonNull(exporter, "exporter");
    return new SimpleLogProcessor(exporter, /* sampled= */ true);
  }

  SimpleLogProcessor(LogExporter logExporter, boolean sampled) {
    this.logExporter = requireNonNull(logExporter, "logExporter");
  }

  @Override
  public void addLogRecord(LogRecord logRecord) {
    try {
      List<LogRecord> spans = Collections.singletonList(logRecord);
      final CompletableResultCode result = logExporter.export(spans);
      pendingExports.add(result);
      result.whenComplete(
          () -> {
            pendingExports.remove(result);
            if (!result.isSuccess()) {
              logger.log(Level.FINE, "Exporter failed");
            }
          });
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Exporter threw an Exception", e);
    }
  }

  @Override
  public CompletableResultCode shutdown() {
    if (isShutdown.getAndSet(true)) {
      return CompletableResultCode.ofSuccess();
    }
    final CompletableResultCode result = new CompletableResultCode();

    final CompletableResultCode flushResult = forceFlush();
    flushResult.whenComplete(
        () -> {
          final CompletableResultCode shutdownResult = logExporter.shutdown();
          shutdownResult.whenComplete(
              () -> {
                if (!flushResult.isSuccess() || !shutdownResult.isSuccess()) {
                  result.fail();
                } else {
                  result.succeed();
                }
              });
        });

    return result;
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofAll(pendingExports);
  }
}
