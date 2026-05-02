/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogRecordBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class ApplicationLogRecordBuilder142 extends ApplicationLogRecordBuilder
    implements application.io.opentelemetry.api.logs.LogRecordBuilder {

  private final LogRecordBuilder agentLogRecordBuilder;

  public ApplicationLogRecordBuilder142(LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setBody(
      @Nullable application.io.opentelemetry.api.common.Value<?> body) {
    agentLogRecordBuilder.setBody(convertValue(body));
    return this;
  }

  @Nullable
  protected static Value<?> convertValue(
      @Nullable application.io.opentelemetry.api.common.Value<?> value) {
    if (value == null) {
      return null;
    }

    switch (value.getType()) {
      case STRING:
        return Value.of((String) value.getValue());
      case BOOLEAN:
        return Value.of((Boolean) value.getValue());
      case LONG:
        return Value.of((Long) value.getValue());
      case DOUBLE:
        return Value.of((Double) value.getValue());
      case ARRAY:
        @SuppressWarnings("unchecked") // type is checked before casting
        List<application.io.opentelemetry.api.common.Value<?>> values =
            (List<application.io.opentelemetry.api.common.Value<?>>) value.getValue();
        List<Value<?>> convertedValues = new ArrayList<>();
        for (application.io.opentelemetry.api.common.Value<?> source : values) {
          convertedValues.add(convertValue(source));
        }
        return Value.of(convertedValues);
      case KEY_VALUE_LIST:
        @SuppressWarnings("unchecked") // type is checked before casting
        List<application.io.opentelemetry.api.common.KeyValue> keyValueList =
            (List<application.io.opentelemetry.api.common.KeyValue>) value.getValue();
        KeyValue[] convertedKeyValueList = new KeyValue[keyValueList.size()];
        int i = 0;
        for (application.io.opentelemetry.api.common.KeyValue source : keyValueList) {
          convertedKeyValueList[i++] =
              KeyValue.of(source.getKey(), convertValue(source.getValue()));
        }
        return Value.of(convertedKeyValueList);
      case BYTES:
        ByteBuffer byteBuffer = (ByteBuffer) value.getValue();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return Value.of(bytes);
    }

    throw new IllegalStateException("Unhandled value type: " + value.getType());
  }
}
