/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.logging;

import io.opentelemetry.common.AttributeConsumer;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;

public class LoggingExporter implements SpanExporter {
  private final String prefix;

  public LoggingExporter(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> list) {
    for (SpanData span : list) {
     logger.logMethod(
          prefix + " " + span.getName() + " " + span.getTraceId() + " " + span.getSpanId() + " ");
      span.getAttributes()
          .forEach(
              new AttributeConsumer() {
                @Override
                public <T> void consume(AttributeKey<T> key, T value) {
                 logger.logMethod(key + "=");
                  switch (key.getType()) {
                    case STRING:
                    logger.logMethod('"' + String.valueOf(value) + '"');
                      break;
                    default:
                      logger.logMethod(value);
                      break;
                  }
                  logger.logMethod(" ");
                }
              });
    }
    logger.logMethod();
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }
}
