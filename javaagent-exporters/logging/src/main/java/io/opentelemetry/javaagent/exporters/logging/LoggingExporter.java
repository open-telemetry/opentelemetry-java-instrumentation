/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.logging;

import io.opentelemetry.api.common.AttributeConsumer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingExporter implements SpanExporter {
  private static final Logger log = LoggerFactory.getLogger(LoggingExporter.class);
  private final String prefix;

  public LoggingExporter(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> list) {
    StringBuilder stringBuilder = new StringBuilder();
    for (SpanData span : list) {

      stringBuilder
          .append(prefix)
          .append(" ")
          .append(span.getName())
          .append(" ")
          .append(span.getTraceId())
          .append(" ")
          .append(span.getSpanId())
          .append(" ");

      span.getAttributes()
          .forEach(
              new AttributeConsumer() {
                @Override
                public <T> void consume(AttributeKey<T> key, T value) {

                  stringBuilder.append(key.getKey()).append('=');

                  if (key.getType() == AttributeType.STRING) {
                    stringBuilder.append('"').append(value).append('"');
                  } else {
                    stringBuilder.append(value);
                  }
                  stringBuilder.append(' ');
                }
              });
    }
    log.info(stringBuilder.toString());
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
