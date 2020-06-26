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

package io.opentelemetry.auto.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AwsSdkClientDecorator extends HttpClientDecorator<Request, Response> {

  static final String COMPONENT_NAME = "java-aws-sdk";

  private final Map<String, String> serviceNames = new ConcurrentHashMap<>();
  private final Map<Class, String> operationNames = new ConcurrentHashMap<>();
  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public AwsSdkClientDecorator(
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public String spanNameForRequest(final Request request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    final String awsServiceName = request.getServiceName();
    final Class<?> awsOperation = request.getOriginalRequest().getClass();
    return remapServiceName(awsServiceName) + "." + remapOperationName(awsOperation);
  }

  @Override
  public Span onRequest(final Span span, final Request request) {
    // Call super first because we override the span name below.
    super.onRequest(span, request);

    final String awsServiceName = request.getServiceName();
    final AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    final Class<?> awsOperation = originalRequest.getClass();

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation.getSimpleName());
    span.setAttribute("aws.endpoint", request.getEndpoint().toString());

    if (contextStore != null) {
      final RequestMeta requestMeta = contextStore.get(originalRequest);
      if (requestMeta != null) {
        span.setAttribute("aws.bucket.name", requestMeta.getBucketName());
        span.setAttribute("aws.queue.url", requestMeta.getQueueUrl());
        span.setAttribute("aws.queue.name", requestMeta.getQueueName());
        span.setAttribute("aws.stream.name", requestMeta.getStreamName());
        span.setAttribute("aws.table.name", requestMeta.getTableName());
      }
    }

    return span;
  }

  @Override
  public Span onResponse(final Span span, final Response response) {
    if (response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      final AmazonWebServiceResponse awsResp = (AmazonWebServiceResponse) response.getAwsResponse();
      span.setAttribute("aws.requestId", awsResp.getRequestId());
    }
    return super.onResponse(span, response);
  }

  private String remapServiceName(final String serviceName) {
    if (!serviceNames.containsKey(serviceName)) {
      serviceNames.put(serviceName, serviceName.replace("Amazon", "").trim());
    }
    return serviceNames.get(serviceName);
  }

  private String remapOperationName(final Class<?> awsOperation) {
    if (!operationNames.containsKey(awsOperation)) {
      operationNames.put(awsOperation, awsOperation.getSimpleName().replace("Request", ""));
    }
    return operationNames.get(awsOperation);
  }

  @Override
  protected String method(final Request request) {
    return request.getHttpMethod().name();
  }

  @Override
  protected URI url(final Request request) {
    return request.getEndpoint();
  }

  @Override
  protected Integer status(final Response response) {
    return response.getHttpResponse().getStatusCode();
  }

  @Override
  protected String userAgent(Request request) {
    return (String) request.getHeaders().get(USER_AGENT);
  }
}
