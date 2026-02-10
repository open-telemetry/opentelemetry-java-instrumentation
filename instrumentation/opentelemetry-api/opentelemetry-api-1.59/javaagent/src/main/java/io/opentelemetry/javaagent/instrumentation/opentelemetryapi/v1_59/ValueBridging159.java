/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.api.common.KeyValue;
import application.io.opentelemetry.api.common.Value;
import application.io.opentelemetry.api.common.ValueType;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Bridges application {@link Value} objects to agent {@link io.opentelemetry.api.common.Value}
 * objects for SDK 1.59.0+.
 *
 * <p>Loaded via reflection by {@link
 * io.opentelemetry.javaagent.instrumentation.opentelemetryapi.ValueBridging}.
 */
public final class ValueBridging159 {

  public static final Function<Object, Object> INSTANCE = ValueBridging159::toAgentValue;

  private static final Logger logger = Logger.getLogger(ValueBridging159.class.getName());

  private ValueBridging159() {}

  @Nullable
  private static Object toAgentValue(@Nullable Object applicationValue) {
    if (applicationValue == null) {
      return null;
    }
    if (!(applicationValue instanceof Value)) {
      logger.log(FINE, "Expected Value but got: {0}", applicationValue.getClass().getName());
      return null;
    }
    return convertValue((Value<?>) applicationValue);
  }

  // Unchecked casts are safe because we switch on the type and cast accordingly
  @SuppressWarnings("unchecked")
  @Nullable
  private static io.opentelemetry.api.common.Value<?> convertValue(Value<?> applicationValue) {
    ValueType type = applicationValue.getType();
    switch (type) {
      case STRING:
        return io.opentelemetry.api.common.Value.of((String) applicationValue.getValue());
      case BOOLEAN:
        return io.opentelemetry.api.common.Value.of((Boolean) applicationValue.getValue());
      case LONG:
        return io.opentelemetry.api.common.Value.of((Long) applicationValue.getValue());
      case DOUBLE:
        return io.opentelemetry.api.common.Value.of((Double) applicationValue.getValue());
      case BYTES:
        ByteBuffer buffer = (ByteBuffer) applicationValue.getValue();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return io.opentelemetry.api.common.Value.of(bytes);
      case ARRAY:
        List<Value<?>> applicationArray = (List<Value<?>>) applicationValue.getValue();
        List<io.opentelemetry.api.common.Value<?>> agentArray = new ArrayList<>();
        for (Value<?> element : applicationArray) {
          io.opentelemetry.api.common.Value<?> agentElement = convertValue(element);
          if (agentElement != null) {
            agentArray.add(agentElement);
          }
        }
        return io.opentelemetry.api.common.Value.of(agentArray);
      case KEY_VALUE_LIST:
        List<KeyValue> applicationKvList = (List<KeyValue>) applicationValue.getValue();
        io.opentelemetry.api.common.KeyValue[] agentKvArray =
            new io.opentelemetry.api.common.KeyValue[applicationKvList.size()];
        int i = 0;
        for (KeyValue applicationKv : applicationKvList) {
          io.opentelemetry.api.common.Value<?> agentKvValue =
              convertValue(applicationKv.getValue());
          if (agentKvValue != null) {
            agentKvArray[i++] =
                io.opentelemetry.api.common.KeyValue.of(applicationKv.getKey(), agentKvValue);
          }
        }
        // Trim array if unexpected Value types were encountered
        if (i < agentKvArray.length) {
          io.opentelemetry.api.common.KeyValue[] trimmed =
              new io.opentelemetry.api.common.KeyValue[i];
          System.arraycopy(agentKvArray, 0, trimmed, 0, i);
          return io.opentelemetry.api.common.Value.of(trimmed);
        }
        return io.opentelemetry.api.common.Value.of(agentKvArray);
      case EMPTY:
        return io.opentelemetry.api.common.Value.empty();
    }
    logger.log(FINE, "unexpected Value type: {0}", type);
    return null;
  }
}
