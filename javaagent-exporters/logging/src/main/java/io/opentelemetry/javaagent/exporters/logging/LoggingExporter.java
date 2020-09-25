/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.exporters.logging;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
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
              new KeyValueConsumer<String, AttributeValue>() {
                @Override
                public void consume(String key, AttributeValue value) {
                  System.out.print(key + "=");
                  switch (value.getType()) {
                    case STRING:
                      System.out.print('"' + value.getStringValue() + '"');
                      break;
                    case BOOLEAN:
                      System.out.print(value.getBooleanValue());
                      break;
                    case LONG:
                      System.out.print(value.getLongValue());
                      break;
                    case DOUBLE:
                      System.out.print(value.getDoubleValue());
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
