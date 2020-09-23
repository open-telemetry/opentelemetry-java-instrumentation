/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdk.getSpanFromAttributes;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkHttpClientTracer.TRACER;
import static io.opentelemetry.instrumentation.awssdk.v2_2.RequestType.ofSdkRequest;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

/** AWS request execution interceptor */
final class TracingExecutionInterceptor implements ExecutionInterceptor {

  static final ExecutionAttribute<Span> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("io.opentelemetry.auto.Span");

  static final ExecutionAttribute<RequestType> REQUEST_TYPE_ATTRIBUTE =
      new ExecutionAttribute<>("io.opentelemetry.auto.aws.RequestType");

  static final String COMPONENT_NAME = "java-aws-sdk";

  private static final Map<RequestType, SdkRequestDecorator> TYPE_TO_DECORATOR = mapDecorators();
  private static final Map<String, String> FIELD_TO_ATTRIBUTE = mapFieldToAttribute();

  private static Map<RequestType, SdkRequestDecorator> mapDecorators() {
    Map<RequestType, SdkRequestDecorator> result = new EnumMap<>(RequestType.class);
    result.put(RequestType.DynamoDB, new DbRequestDecorator());
    return result;
  }

  private static Map<String, String> mapFieldToAttribute() {
    Map<String, String> result = new HashMap<>();
    result.put("QueueUrl", "aws.queue.url");
    result.put("Bucket", "aws.bucket.name");
    result.put("QueueName", "aws.queue.name");
    result.put("StreamName", "aws.stream.name");
    result.put("TableName", "aws.table.name");
    return result;
  }

  private final Kind kind;

  TracingExecutionInterceptor(Kind kind) {
    this.kind = kind;
  }

  @Nullable
  private SdkRequestDecorator decorator(ExecutionAttributes executionAttributes) {
    RequestType type = getTypeFromAttributes(executionAttributes);
    return TYPE_TO_DECORATOR.get(type);
  }

  private RequestType getTypeFromAttributes(ExecutionAttributes executionAttributes) {
    return executionAttributes.getAttribute(REQUEST_TYPE_ATTRIBUTE);
  }

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    Span span = TRACER.getOrCreateSpan(spanName(executionAttributes), AwsSdk.tracer(), kind);
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
    RequestType type = ofSdkRequest(context.request());
    if (type != null) {
      executionAttributes.putAttribute(REQUEST_TYPE_ATTRIBUTE, type);
    }
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
    Span span = getSpanFromAttributes(executionAttributes);
    if (span != null) {
      TRACER.onRequest(span, context.httpRequest());
      SdkRequestDecorator decorator = decorator(executionAttributes);
      if (decorator != null) {
        decorator.decorate(span, context.request(), executionAttributes);
      }
      decorateWithGenericRequestData(span, context.request());
      decorateWithExAttributesData(span, executionAttributes);
    }
  }

  private void decorateWithGenericRequestData(Span span, SdkRequest request) {

    RequestType type = ofSdkRequest(request);
    if (type != null) {
      for (String field : type.getFields()) {
        request
            .getValueForField(field, String.class)
            .ifPresent(val -> span.setAttribute(FIELD_TO_ATTRIBUTE.get(field), val));
      }
    }
  }

  private void decorateWithExAttributesData(Span span, ExecutionAttributes attributes) {

    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation);
  }

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    Span span = getSpanFromAttributes(executionAttributes);
    if (span != null) {
      clearAttributes(executionAttributes);
      TRACER.afterExecution(span, context.httpRequest());
      onSdkResponse(span, context.response());
      TRACER.end(span, context.httpResponse());
    }
  }

  private void onSdkResponse(Span span, SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    Span span = getSpanFromAttributes(executionAttributes);
    if (span != null) {
      clearAttributes(executionAttributes);
      TRACER.endExceptionally(span, context.exception());
    }
  }

  private void clearAttributes(ExecutionAttributes executionAttributes) {
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
    executionAttributes.putAttribute(REQUEST_TYPE_ATTRIBUTE, null);
  }

  private String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    return awsServiceName + "." + awsOperation;
  }
}
