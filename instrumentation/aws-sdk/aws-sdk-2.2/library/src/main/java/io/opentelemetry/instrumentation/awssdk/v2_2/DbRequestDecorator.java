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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

final class DbRequestDecorator implements SdkRequestDecorator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  @Override
  public void decorate(Span span, SdkRequest sdkRequest, ExecutionAttributes attributes) {

    span.setAttribute(SemanticAttributes.DB_SYSTEM, "dynamodb");
    // decorate with TableName as db.name (DynamoDB equivalent - not for batch)
    sdkRequest
        .getValueForField("TableName", String.class)
        .ifPresent(val -> span.setAttribute(SemanticAttributes.DB_NAME, val));

    String operation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    if (operation != null) {
      span.setAttribute(SemanticAttributes.DB_OPERATION, operation);
    }

    DynamoDbRequest request = DynamoDbRequest.ofSdkRequest(sdkRequest);
    if (request != null) {
      mapRequestFields(request, sdkRequest, span);
    }
  }

  private final void mapRequestFields(DynamoDbRequest request, SdkRequest sdkRequest, Span span) {
    for (FieldMapping fieldMapping : request.fields()) {
      mapRequestField(fieldMapping, sdkRequest, span);
    }
  }

  private final void mapRequestField(FieldMapping fieldMapping, SdkRequest sdkRequest, Span span) {
    // traverse path
    String[] path = fieldMapping.getFields();
    Object target = sdkRequest;
    for (int i = 0; i < path.length && target != null; i++) {
      target = next(target, path[i]);
    }
    if (target != null) {
      String value = serialize(target);
      if (value != null && !value.isEmpty()) {
        span.setAttribute(fieldMapping.getAttribute(), value);
      }
    }
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
      return ((List<Object>) target).stream().map(this::serialize).collect(Collectors.joining());
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
