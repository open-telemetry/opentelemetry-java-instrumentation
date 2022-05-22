/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_ENDPOINT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_TABLE_NAME;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.function.Function;
import javax.annotation.Nullable;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<Request<?>, Response<?>> {
  private static final String COMPONENT_NAME = "java-aws-sdk";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    attributes.put(AWS_AGENT, COMPONENT_NAME);
    attributes.put(AWS_ENDPOINT, request.getEndpoint().toString());

    Object originalRequest = request.getOriginalRequest();
    setRequestAttribute(attributes, AWS_BUCKET_NAME, originalRequest, RequestAccess::getBucketName);
    setRequestAttribute(attributes, AWS_QUEUE_URL, originalRequest, RequestAccess::getQueueUrl);
    setRequestAttribute(attributes, AWS_QUEUE_NAME, originalRequest, RequestAccess::getQueueName);
    setRequestAttribute(attributes, AWS_STREAM_NAME, originalRequest, RequestAccess::getStreamName);
    setRequestAttribute(attributes, AWS_TABLE_NAME, originalRequest, RequestAccess::getTableName);
  }

  private static void setRequestAttribute(
      AttributesBuilder attributes,
      AttributeKey<String> key,
      Object request,
      Function<Object, String> getter) {
    String value = getter.apply(request);
    if (value != null) {
      attributes.put(key, value);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {
    if (response != null && response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      AmazonWebServiceResponse<?> awsResp = (AmazonWebServiceResponse<?>) response.getAwsResponse();
      String requestId = awsResp.getRequestId();
      if (requestId != null) {
        attributes.put(AWS_REQUEST_ID, requestId);
      }
    }
  }
}
