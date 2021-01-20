/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.ConcurrentHashMap;

/** Tracer support class shared between AWS SDK tracers. */
public class AwsSdkTracerSupport {

  private final NamesCache namesCache = new NamesCache();

  public String spanNameForRequest(Request<?> request, String defaultSpanName) {
    if (request == null) {
      return defaultSpanName;
    }
    String awsServiceName = request.getServiceName();
    Class<?> awsOperation = request.getOriginalRequest().getClass();
    return qualifiedOperation(awsServiceName, awsOperation);
  }

  private String qualifiedOperation(String service, Class<?> operation) {
    ConcurrentHashMap<String, String> cache = namesCache.get(operation);
    String qualified = cache.get(service);
    if (qualified == null) {
      qualified =
          service.replace("Amazon", "").trim()
              + '.'
              + operation.getSimpleName().replace("Request", "");
      cache.put(service, qualified);
    }
    return qualified;
  }

  /** Enhances new span with AWS specific metadata. */
  public void onNewSpan(Context newSpanContext, Request<?> request, RequestMeta requestMeta) {
    Span span = Span.fromContext(newSpanContext);

    String awsServiceName = request.getServiceName();
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    Class<?> awsOperation = originalRequest.getClass();

    span.setAttribute("aws.agent", "java-aws-sdk");
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation.getSimpleName());
    span.setAttribute("aws.endpoint", request.getEndpoint().toString());

    if (requestMeta != null) {
      span.setAttribute("aws.bucket.name", requestMeta.getBucketName());
      span.setAttribute("aws.queue.url", requestMeta.getQueueUrl());
      span.setAttribute("aws.queue.name", requestMeta.getQueueName());
      span.setAttribute("aws.stream.name", requestMeta.getStreamName());
      span.setAttribute("aws.table.name", requestMeta.getTableName());
    }
  }

  public void onResponse(Span span, Response<?> response) {
    if (response != null && response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      AmazonWebServiceResponse awsResp = (AmazonWebServiceResponse) response.getAwsResponse();
      span.setAttribute("aws.requestId", awsResp.getRequestId());
    }
  }

  static final class NamesCache extends ClassValue<ConcurrentHashMap<String, String>> {
    @Override
    protected ConcurrentHashMap<String, String> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  }
}
