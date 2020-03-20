/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.exporters.loggingexporter;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.List;
import java.util.Map;

public class LoggingExporter implements SpanExporter {
  private final String prefix;

  public LoggingExporter(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public ResultCode export(final List<SpanData> list) {
    for (final SpanData span : list) {
      System.out.print(
          prefix + " " + span.getName() + " " + span.getSpanId().toLowerBase16() + " ");
      for (final Map.Entry<String, AttributeValue> attr : span.getAttributes().entrySet()) {
        System.out.print(attr.getKey() + "=");
        final AttributeValue value = attr.getValue();
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
    }
    System.out.println();
    return ResultCode.SUCCESS;
  }

  @Override
  public void shutdown() {}
}
