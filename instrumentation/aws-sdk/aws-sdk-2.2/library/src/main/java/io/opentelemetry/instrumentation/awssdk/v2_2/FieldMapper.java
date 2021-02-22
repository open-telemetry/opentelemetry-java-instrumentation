/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import java.util.List;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.utils.StringUtils;

class FieldMapper {

  private final Serializer serializer;
  private final MethodHandleFactory methodHandleFactory;

  FieldMapper() {
    serializer = new Serializer();
    methodHandleFactory = new MethodHandleFactory();
  }

  FieldMapper(Serializer serializer, MethodHandleFactory methodHandleFactory) {
    this.methodHandleFactory = methodHandleFactory;
    this.serializer = serializer;
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
    // traverse path
    List<String> path = fieldMapping.getFields();
    Object target = fieldValueProvider.apply(path.get(0));
    for (int i = 1; i < path.size() && target != null; i++) {
      target = next(target, path.get(i));
    }
    if (target != null) {
      String value = serializer.serialize(target);
      if (!StringUtils.isEmpty(value)) {
        span.setAttribute(fieldMapping.getAttribute(), value);
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
