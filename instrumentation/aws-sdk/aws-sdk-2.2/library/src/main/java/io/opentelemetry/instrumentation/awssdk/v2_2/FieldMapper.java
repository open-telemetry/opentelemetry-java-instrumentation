/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.SdkRequest;

public class FieldMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  public void mapFields(AwsSdkRequest request, SdkRequest sdkRequest, Span span) {
    for (FieldMapping fieldMapping : request.fields()) {
      mapFields(fieldMapping, sdkRequest, span);
    }
    for (FieldMapping fieldMapping : request.type().fields()) {
      mapFields(fieldMapping, sdkRequest, span);
    }
  }

  private void mapFields(FieldMapping fieldMapping, SdkRequest sdkRequest, Span span) {
    // traverse path
    String[] path = fieldMapping.getFields();
    Object target = sdkRequest.getValueForField(camelCase(path[0]), Object.class).orElse(null);
    for (int i = 1; i < path.length && target != null; i++) {
      target = next(target, path[i]);
    }
    if (target != null) {
      String value = serialize(target);
      if (value != null && !value.isEmpty()) {
        span.setAttribute(fieldMapping.getAttribute(), value);
      }
    }
  }

  private String camelCase(String string) {
    return string.substring(0, 1).toUpperCase() + string.substring(1);
  }

  @Nullable
  private String serialize(Object target) {
    if (target instanceof SdkPojo) {
      try {
        return OBJECT_MAPPER.writeValueAsString(target);
      } catch (JsonProcessingException e) {
        return null;
      }
    }
    if (target instanceof List) {
      List<Object> list = (List<Object>) target;
      return list.stream().map(this::serialize).collect(Collectors.joining());
    }
    // simple type
    return target.toString();
  }

  @Nullable
  private Object next(Object current, String fieldName) {
    try {
      Method method = current.getClass().getMethod(fieldName);
      return method.invoke(current);
    } catch (Exception e) {
      // ignore
    }
    return null;
  }
}
