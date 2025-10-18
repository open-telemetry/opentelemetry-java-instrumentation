/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.trace.Span;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.utils.StringUtils;

class FieldMapper {

  private final Serializer serializer;
  private final MethodHandleFactory methodHandleFactory;
  private final boolean captureExperimentalSpanAttributes;

  FieldMapper(boolean captureExperimentalSpanAttributes) {
    this(new Serializer(), new MethodHandleFactory(), captureExperimentalSpanAttributes);
  }

  // visible for testing
  FieldMapper(Serializer serializer, MethodHandleFactory methodHandleFactory) {
    this(serializer, methodHandleFactory, true);
  }

  private FieldMapper(
      Serializer serializer,
      MethodHandleFactory methodHandleFactory,
      boolean captureExperimentalSpanAttributes) {
    this.methodHandleFactory = methodHandleFactory;
    this.serializer = serializer;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  void mapToAttributes(SdkRequest sdkRequest, AwsSdkRequest request, Span span) {
    mapToAttributes(
        field -> sdkRequest.getValueForField(field, Object.class).orElse(null),
        FieldMapping.Type.REQUEST,
        request,
        span);
  }

  void mapToAttributes(SdkResponse sdkResponse, AwsSdkRequest request, Span span) {
    mapToAttributes(
        field -> sdkResponse.getValueForField(field, Object.class).orElse(null),
        FieldMapping.Type.RESPONSE,
        request,
        span);
  }

  private void mapToAttributes(
      Function<String, Object> fieldValueProvider,
      FieldMapping.Type type,
      AwsSdkRequest request,
      Span span) {
    for (FieldMapping fieldMapping : request.fields(type)) {
      mapToAttributes(fieldValueProvider, fieldMapping, span);
    }
    for (FieldMapping fieldMapping : request.type().fields(type)) {
      mapToAttributes(fieldValueProvider, fieldMapping, span);
    }
  }

  private void mapToAttributes(
      Function<String, Object> fieldValueProvider, FieldMapping fieldMapping, Span span) {
    if (!captureExperimentalSpanAttributes && fieldMapping.isExperimental()) {
      return;
    }

    // traverse path
    List<String> path = fieldMapping.getFields();
    Object target = fieldValueProvider.apply(path.get(0));
    for (int i = 1; i < path.size() && target != null; i++) {
      target = next(target, path.get(i));
    }
    if (target != null) {
      switch (fieldMapping.getAttributeType()) {
        case STRING:
          String stringValue = serializer.serialize(target);
          if (!StringUtils.isEmpty(stringValue)) {
            span.setAttribute(fieldMapping.getAttributeKey(), stringValue);
          }
          break;
        case DOUBLE:
          if (target instanceof Number) {
            span.setAttribute(fieldMapping.getAttributeKey(), ((Number) target).doubleValue());
          }
          break;
        case LONG:
          if (target instanceof Number) {
            span.setAttribute(fieldMapping.getAttributeKey(), ((Number) target).longValue());
          } else if (target instanceof Collection) {
            // map to collection size
            span.setAttribute(fieldMapping.getAttributeKey(), ((Collection<?>) target).size());
          }
          break;
        case BOOLEAN:
          if (target instanceof Boolean) {
            span.setAttribute(fieldMapping.getAttributeKey(), (Boolean) target);
          }
          break;
        case STRING_ARRAY:
          if (target instanceof Map) {
            target = ((Map<?, ?>) target).keySet();
          }
          if (target instanceof Collection) {
            List<String> value = serializer.serializeCollection((Collection<?>) target);
            if (!value.isEmpty()) {
              span.setAttribute(fieldMapping.getAttributeKey(), value);
            }
          } else {
            String value = serializer.serialize(target);
            if (!StringUtils.isEmpty(value)) {
              span.setAttribute(fieldMapping.getAttributeKey(), Collections.singletonList(value));
            }
          }
          break;
        default:
          // shouldn't reach here because FieldMapping constructor already rejects other attribute
          // types
          throw new IllegalStateException(
              "Unsupported attribute type: " + fieldMapping.getAttributeType());
      }
    }
  }

  @Nullable
  private Object next(Object current, String fieldName) {
    try {
      return methodHandleFactory.forField(current.getClass(), fieldName).invoke(current);
    } catch (Throwable t) {
      // ignore
    }
    return null;
  }
}
