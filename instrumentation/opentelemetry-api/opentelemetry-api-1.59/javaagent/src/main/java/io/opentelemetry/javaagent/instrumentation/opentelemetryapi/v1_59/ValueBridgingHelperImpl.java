/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.api.common.KeyValue;
import application.io.opentelemetry.api.common.Value;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of VALUE type bridging for SDK 1.59.0+.
 *
 * <p>This class handles conversion of application {@link Value} objects to agent {@link
 * io.opentelemetry.api.common.Value} objects. It is loaded via reflection by {@link
 * io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging}.
 */
public class ValueBridgingHelperImpl implements Bridging.ValueBridgingHelper {

  private static final Logger logger = Logger.getLogger(ValueBridgingHelperImpl.class.getName());

  /** Default constructor - required for reflection-based instantiation. */
  public ValueBridgingHelperImpl() {}

  @Override
  public Object toAgentValue(Object applicationValue) {
    if (applicationValue == null) {
      return null;
    }

    if (!(applicationValue instanceof Value)) {
      logger.log(FINE, "Expected Value but got: {0}", applicationValue.getClass().getName());
      return null;
    }

    return convertValue((Value<?>) applicationValue);
  }

  // Suppress unchecked cast warnings - values are cast based on type name which should be safe
  @SuppressWarnings("unchecked")
  private static io.opentelemetry.api.common.Value<?> convertValue(Value<?> applicationValue) {
    String typeName = applicationValue.getType().name();
    switch (typeName) {
      case "STRING":
        return io.opentelemetry.api.common.Value.of((String) applicationValue.getValue());
      case "BOOLEAN":
        return io.opentelemetry.api.common.Value.of((Boolean) applicationValue.getValue());
      case "LONG":
        return io.opentelemetry.api.common.Value.of((Long) applicationValue.getValue());
      case "DOUBLE":
        return io.opentelemetry.api.common.Value.of((Double) applicationValue.getValue());
      case "BYTES":
        ByteBuffer buffer = (ByteBuffer) applicationValue.getValue();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return io.opentelemetry.api.common.Value.of(bytes);
      case "ARRAY":
        List<Value<?>> applicationArray = (List<Value<?>>) applicationValue.getValue();
        List<io.opentelemetry.api.common.Value<?>> agentArray = new ArrayList<>();
        for (Value<?> element : applicationArray) {
          io.opentelemetry.api.common.Value<?> agentElement = convertValue(element);
          if (agentElement != null) {
            agentArray.add(agentElement);
          }
        }
        return io.opentelemetry.api.common.Value.of(agentArray);
      case "KEY_VALUE_LIST":
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
        // Trim array if any elements were skipped
        if (i < agentKvArray.length) {
          io.opentelemetry.api.common.KeyValue[] trimmed =
              new io.opentelemetry.api.common.KeyValue[i];
          System.arraycopy(agentKvArray, 0, trimmed, 0, i);
          return io.opentelemetry.api.common.Value.of(trimmed);
        }
        return io.opentelemetry.api.common.Value.of(agentKvArray);
      case "EMPTY":
        return io.opentelemetry.api.common.Value.empty();
      default:
        logger.log(FINE, "unexpected Value type: {0}", typeName);
        return null;
    }
  }
}
