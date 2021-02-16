/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;

public class FieldMapper {

  private final Serializer serializer = new Serializer();

  private final ClassValue<ConcurrentHashMap<String, MethodHandle>> getterCache =
      new ClassValue<ConcurrentHashMap<String, MethodHandle>>() {
        @Override
        protected ConcurrentHashMap<String, MethodHandle> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public void mapFields(SdkRequest sdkRequest, AwsSdkRequest request, Span span) {
    mapFields(
        field -> sdkRequest.getValueForField(field, Object.class).orElse(null), request, span);
  }

  public void mapFields(SdkResponse sdkResponse, AwsSdkRequest request, Span span) {
    mapFields(
        field -> sdkResponse.getValueForField(field, Object.class).orElse(null), request, span);
  }

  private void mapFields(
      Function<String, Object> fieldValueProvider, AwsSdkRequest request, Span span) {
    for (FieldMapping fieldMapping : request.fields()) {
      mapFields(fieldValueProvider, fieldMapping, span);
    }
    for (FieldMapping fieldMapping : request.type().fields()) {
      mapFields(fieldValueProvider, fieldMapping, span);
    }
  }

  private void mapFields(
      Function<String, Object> fieldValueProvider, FieldMapping fieldMapping, Span span) {
    // traverse path
    String[] path = fieldMapping.getFields();
    Object target = fieldValueProvider.apply(camelCase(path[0]));
    for (int i = 1; i < path.length && target != null; i++) {
      target = next(target, path[i]);
    }
    if (target != null) {
      String value = serializer.serialize(target);
      if (value != null && !value.isEmpty()) {
        span.setAttribute(fieldMapping.getAttribute(), value);
      }
    }
  }

  private String camelCase(String string) {
    return string.substring(0, 1).toUpperCase() + string.substring(1);
  }

  @Nullable
  private Object next(Object current, String fieldName) {
    try {
      return forField(current.getClass(), fieldName).invoke(current);
    } catch (Throwable t) {
      // ignore
    }
    return null;
  }

  private MethodHandle forField(Class clazz, String fieldName)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle methodHandle = getterCache.get(clazz).get(fieldName);
    if (methodHandle == null) {
      methodHandle = MethodHandles.publicLookup().unreflect(clazz.getMethod(fieldName));
      getterCache.get(clazz).put(fieldName, methodHandle);
    }
    return methodHandle;
  }
}
