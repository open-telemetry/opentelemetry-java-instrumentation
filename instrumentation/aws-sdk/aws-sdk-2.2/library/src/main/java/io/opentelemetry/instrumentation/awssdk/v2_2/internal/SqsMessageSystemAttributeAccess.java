/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

final class SqsMessageSystemAttributeAccess {
  @Nullable private static final Accessors accessors;

  static {
    Accessors localAccessors = null;
    try {
      Class<?> valueClass =
          Class.forName("software.amazon.awssdk.services.sqs.model.MessageSystemAttributeValue");
      Class<?> valueBuilderClass =
          Class.forName(
              "software.amazon.awssdk.services.sqs.model.MessageSystemAttributeValue$Builder");
      Class<?> entryBuilderClass =
          Class.forName(
              "software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry$Builder");
      localAccessors =
          new Accessors(
              SendMessageBatchRequestEntry.class.getMethod("messageSystemAttributesAsStrings"),
              entryBuilderClass.getMethod("messageSystemAttributesWithStrings", Map.class),
              valueClass.getMethod("stringValue"),
              valueClass.getMethod("builder"),
              valueBuilderClass.getMethod("dataType", String.class),
              valueBuilderClass.getMethod("stringValue", String.class),
              valueBuilderClass.getMethod("build"));
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
    Object valueBuilder = invoke(accessors.newValueBuilder, null, Object.class);
    invoke(accessors.setDataType, valueBuilder, Object.class, "String");
    invoke(accessors.setStringValue, valueBuilder, Object.class, traceHeader);
    attributes.put(
        SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE,
        invoke(accessors.buildValue, valueBuilder, Object.class));

    SendMessageBatchRequestEntry.Builder entryBuilder = entry.toBuilder();
    invoke(accessors.setMessageSystemAttributes, entryBuilder, Object.class, attributes);
    return entryBuilder.build();
  }

  private static <T> T invoke(Method method, Object target, Class<T> returnType, Object... args) {
    try {
      return returnType.cast(method.invoke(target, args));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Could not access SQS message system attributes", e);
    }
  }

  private static final class Accessors {
    private final Method getMessageSystemAttributes;
    private final Method setMessageSystemAttributes;
    private final Method getStringValue;
    private final Method newValueBuilder;
    private final Method setDataType;
    private final Method setStringValue;
    private final Method buildValue;

    private Accessors(
        Method getMessageSystemAttributes,
        Method setMessageSystemAttributes,
        Method getStringValue,
        Method newValueBuilder,
        Method setDataType,
        Method setStringValue,
        Method buildValue) {
      this.getMessageSystemAttributes = getMessageSystemAttributes;
      this.setMessageSystemAttributes = setMessageSystemAttributes;
      this.getStringValue = getStringValue;
      this.newValueBuilder = newValueBuilder;
      this.setDataType = setDataType;
      this.setStringValue = setStringValue;
      this.buildValue = buildValue;
    }
  }

  private SqsMessageSystemAttributeAccess() {}
}
