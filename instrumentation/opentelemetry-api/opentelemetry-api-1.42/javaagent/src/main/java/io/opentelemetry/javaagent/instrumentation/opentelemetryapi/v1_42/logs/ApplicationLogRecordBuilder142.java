/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs;

import application.io.opentelemetry.api.common.KeyValue;
import application.io.opentelemetry.api.common.Value;
import application.io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogRecordBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ApplicationLogRecordBuilder142 extends ApplicationLogRecordBuilder
    implements LogRecordBuilder {

  private final io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder;

  public ApplicationLogRecordBuilder142(
      io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  public LogRecordBuilder setBody(Value<?> body) {
    agentLogRecordBuilder.setBody(convertValue(body));
    return this;
  }

  @SuppressWarnings("unchecked")
  private static io.opentelemetry.api.common.Value<?> convertValue(Value<?> value) {
    if (value == null) {
      return null;
    }

    switch (value.getType()) {
      case STRING:
        return io.opentelemetry.api.common.Value.of((String) value.getValue());
      case BOOLEAN:
        return io.opentelemetry.api.common.Value.of((Boolean) value.getValue());
      case LONG:
        return io.opentelemetry.api.common.Value.of((Long) value.getValue());
      case DOUBLE:
        return io.opentelemetry.api.common.Value.of((Double) value.getValue());
      case ARRAY:
        List<Value<?>> values = (List<Value<?>>) value.getValue();
        List<io.opentelemetry.api.common.Value<?>> convertedValues = new ArrayList<>();
        for (Value<?> source : values) {
          convertedValues.add(convertValue(source));
        }
        return io.opentelemetry.api.common.Value.of(convertedValues);
      case KEY_VALUE_LIST:
        List<KeyValue> keyValueList = (List<KeyValue>) value.getValue();
        io.opentelemetry.api.common.KeyValue[] convertedKeyValueList =
            new io.opentelemetry.api.common.KeyValue[keyValueList.size()];
        int i = 0;
        for (KeyValue source : keyValueList) {
          convertedKeyValueList[i++] =
              io.opentelemetry.api.common.KeyValue.of(
                  source.getKey(), convertValue(source.getValue()));
        }
        return io.opentelemetry.api.common.Value.of(convertedKeyValueList);
      case BYTES:
        ByteBuffer byteBuffer = (ByteBuffer) value.getValue();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        break;
    }

    throw new IllegalStateException("Unhandled value type: " + value.getType());
  }
}
