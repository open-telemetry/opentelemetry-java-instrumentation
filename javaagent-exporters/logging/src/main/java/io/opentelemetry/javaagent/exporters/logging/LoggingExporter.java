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
      System.out.print(
          prefix + " " + span.getName() + " " + span.getTraceId() + " " + span.getSpanId() + " ");
      span.getAttributes()
          .forEach(
              new AttributeConsumer() {
                @Override
                public <T> void consume(AttributeKey<T> key, T value) {
                  System.out.print(key + "=");
                  switch (key.getType()) {
                    case STRING:
                      System.out.print('"' + String.valueOf(value) + '"');
                      break;
                    default:
                      System.out.print(value);
                      break;
                  }
                  System.out.print(" ");
                }
              });
    }
    System.out.println();
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
