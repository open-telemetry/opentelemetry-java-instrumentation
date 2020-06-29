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

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkClientDecorator extends HttpClientDecorator<SdkHttpRequest, SdkHttpResponse> {
  static final AwsSdkClientDecorator DECORATE = new AwsSdkClientDecorator();

  static final String COMPONENT_NAME = "java-aws-sdk";

  Span onSdkRequest(final Span span, final SdkRequest request) {
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

  String spanName(final ExecutionAttributes attributes) {
    final String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    final String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    return awsServiceName + "." + awsOperation;
  }

  Span onAttributes(final Span span, final ExecutionAttributes attributes) {

    final String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    final String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation);

    return span;
  }

  // Certain headers in the request like User-Agent are only available after execution.
  Span afterExecution(final Span span, final SdkHttpRequest request) {
    SemanticAttributes.HTTP_USER_AGENT.set(span, userAgent(request));
    return span;
  }

  // Not overriding the super.  Should call both with each type of response.
  Span onSdkResponse(final Span span, final SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
    return span;
  }

  @Override
  protected String method(final SdkHttpRequest request) {
    return request.method().name();
  }

  @Override
  protected URI url(final SdkHttpRequest request) {
    return request.getUri();
  }

  @Override
  protected Integer status(final SdkHttpResponse response) {
    return response.statusCode();
  }

  @Override
  protected String userAgent(SdkHttpRequest sdkHttpRequest) {
    return sdkHttpRequest.firstMatchingHeader(USER_AGENT).orElse(null);
  }
}
