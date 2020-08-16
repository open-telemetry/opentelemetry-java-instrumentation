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

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
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

  static final String COMPONENT_NAME = "java-aws-sdk";

  private final Kind kind;

  TracingExecutionInterceptor(Kind kind) {
    this.kind = kind;
  }

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    Span span = TRACER.getOrCreateSpan(spanName(executionAttributes), AwsSdk.tracer());
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      TRACER.onRequest(span, context.httpRequest());
      onSdkRequest(span, context.request());
      onAttributes(span, executionAttributes);
    }
  }

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      TRACER.afterExecution(span, context.httpRequest());
      onSdkResponse(span, context.response());
      TRACER.end(span, context.httpResponse());
    }
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      TRACER.endExceptionally(span, context.exception());
    }
  }

  Span onSdkRequest(Span span, SdkRequest request) {
    // S3
    request
        .getValueForField("Bucket", String.class)
        .ifPresent(name -> span.setAttribute("aws.bucket.name", name));
    // SQS
    request
        .getValueForField("QueueUrl", String.class)
        .ifPresent(name -> span.setAttribute("aws.queue.url", name));
    request
        .getValueForField("QueueName", String.class)
        .ifPresent(name -> span.setAttribute("aws.queue.name", name));
    // Kinesis
    request
        .getValueForField("StreamName", String.class)
        .ifPresent(name -> span.setAttribute("aws.stream.name", name));
    // DynamoDB
    request
        .getValueForField("TableName", String.class)
        .ifPresent(name -> span.setAttribute("aws.table.name", name));
    return span;
  }

  String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    return awsServiceName + "." + awsOperation;
  }

  Span onAttributes(Span span, ExecutionAttributes attributes) {

    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation);

    return span;
  }

  // Not overriding the super.  Should call both with each type of response.
  Span onSdkResponse(Span span, SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
    return span;
  }
}
