/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

class SqsMessageSystemAttributeAccess {
  @Nullable private static final Accessors accessors;

  static {
    Accessors localAccessors = null;
    try {
      Class<?> valueClass =
          Class.forName("com.amazonaws.services.sqs.model.MessageSystemAttributeValue");
      localAccessors =
          new Accessors(
              valueClass.getConstructor(),
              SendMessageBatchRequestEntry.class.getMethod("getMessageSystemAttributes"),
              SendMessageBatchRequestEntry.class.getMethod("setMessageSystemAttributes", Map.class),
              valueClass.getMethod("getStringValue"),
              valueClass.getMethod("withDataType", String.class),
              valueClass.getMethod("withStringValue", String.class));
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      // Older AWS SDK versions do not support message system attributes on batch entries.
    }
    accessors = localAccessors;
  }

  static boolean isAvailable() {
    return accessors != null;
  }

  @Nullable
  static String getTraceHeader(SendMessageBatchRequestEntry entry) {
    Accessors accessors = SqsMessageSystemAttributeAccess.accessors;
    if (accessors == null) {
      return null;
    }
    Map<?, ?> attributes = invoke(accessors.getMessageSystemAttributes, entry, Map.class);
    Object value = attributes.get(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
    return value == null ? null : invoke(accessors.getStringValue, value, String.class);
  }

  @Nullable
  static SendMessageBatchRequestEntry withTraceHeader(
      SendMessageBatchRequestEntry entry, String traceHeader) {
    Accessors accessors = SqsMessageSystemAttributeAccess.accessors;
    if (accessors == null) {
      return null;
    }

    Map<?, ?> existingAttributes = invoke(accessors.getMessageSystemAttributes, entry, Map.class);
    if (existingAttributes.containsKey(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE)) {
      return null;
    }
    Map<Object, Object> attributes = new HashMap<>(existingAttributes);
    Object value = newValue(accessors.valueConstructor);
    invoke(accessors.withDataType, value, Object.class, "String");
    invoke(accessors.withStringValue, value, Object.class, traceHeader);
    attributes.put(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE, value);

    SendMessageBatchRequestEntry updatedEntry = entry.clone();
    invoke(accessors.setMessageSystemAttributes, updatedEntry, Object.class, attributes);
    return updatedEntry;
  }

  private static Object newValue(Constructor<?> valueConstructor) {
    try {
      return valueConstructor.newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Could not create an SQS message system attribute", e);
    }
  }

  private static <T> T invoke(Method method, Object target, Class<T> returnType, Object... args) {
    try {
      return returnType.cast(method.invoke(target, args));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Could not access SQS message system attributes", e);
    }
  }

  private static final class Accessors {
    private final Constructor<?> valueConstructor;
    private final Method getMessageSystemAttributes;
    private final Method setMessageSystemAttributes;
    private final Method getStringValue;
    private final Method withDataType;
    private final Method withStringValue;

    private Accessors(
        Constructor<?> valueConstructor,
        Method getMessageSystemAttributes,
        Method setMessageSystemAttributes,
        Method getStringValue,
        Method withDataType,
        Method withStringValue) {
      this.valueConstructor = valueConstructor;
      this.getMessageSystemAttributes = getMessageSystemAttributes;
      this.setMessageSystemAttributes = setMessageSystemAttributes;
      this.getStringValue = getStringValue;
      this.withDataType = withDataType;
      this.withStringValue = withStringValue;
    }
  }

  private SqsMessageSystemAttributeAccess() {}
}
