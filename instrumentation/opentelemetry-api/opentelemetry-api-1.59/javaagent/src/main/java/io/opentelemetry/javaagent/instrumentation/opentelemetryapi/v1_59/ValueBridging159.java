/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
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
    if (!(applicationValue instanceof application.io.opentelemetry.api.common.Value)) {
      logger.log(
          FINE,
          "Expected application.io.opentelemetry.api.common.Value but got: {0}",
          applicationValue.getClass().getName());
      return null;
    }
    return convertValue((application.io.opentelemetry.api.common.Value<?>) applicationValue);
  }

  // Unchecked casts are safe because we switch on the type and cast accordingly
  @SuppressWarnings("unchecked")
  @Nullable
  private static Value<?> convertValue(
      application.io.opentelemetry.api.common.Value<?> applicationValue) {
    application.io.opentelemetry.api.common.ValueType type = applicationValue.getType();
    switch (type) {
      case STRING:
        return Value.of((String) applicationValue.getValue());
      case BOOLEAN:
        return Value.of((Boolean) applicationValue.getValue());
      case LONG:
        return Value.of((Long) applicationValue.getValue());
      case DOUBLE:
        return Value.of((Double) applicationValue.getValue());
      case BYTES:
        ByteBuffer buffer = (ByteBuffer) applicationValue.getValue();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return Value.of(bytes);
      case ARRAY:
        List<application.io.opentelemetry.api.common.Value<?>> applicationArray =
            (List<application.io.opentelemetry.api.common.Value<?>>) applicationValue.getValue();
        List<Value<?>> agentArray = new ArrayList<>();
        for (application.io.opentelemetry.api.common.Value<?> element : applicationArray) {
          Value<?> agentElement = convertValue(element);
          if (agentElement != null) {
            agentArray.add(agentElement);
          }
        }
        return Value.of(agentArray);
      case KEY_VALUE_LIST:
        List<application.io.opentelemetry.api.common.KeyValue> applicationKvList =
            (List<application.io.opentelemetry.api.common.KeyValue>) applicationValue.getValue();
        KeyValue[] agentKvArray = new KeyValue[applicationKvList.size()];
        int i = 0;
        for (application.io.opentelemetry.api.common.KeyValue applicationKv : applicationKvList) {
          Value<?> agentKvValue = convertValue(applicationKv.getValue());
          if (agentKvValue != null) {
            agentKvArray[i++] = KeyValue.of(applicationKv.getKey(), agentKvValue);
          }
        }
        // Trim array if unexpected Value types were encountered
        if (i < agentKvArray.length) {
          KeyValue[] trimmed = new KeyValue[i];
          System.arraycopy(agentKvArray, 0, trimmed, 0, i);
          return Value.of(trimmed);
        }
        return Value.of(agentKvArray);
      case EMPTY:
        return Value.empty();
    }
    logger.log(FINE, "unexpected application.io.opentelemetry.api.common.Value type: {0}", type);
    return null;
  }
}
