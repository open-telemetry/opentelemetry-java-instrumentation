/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_ENDPOINT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_OPERATION;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_SERVICE;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_TABLE_NAME;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

class AwsSdkExperimentalAttributesExtractor extends AttributesExtractor<Request<?>, Response<?>> {
  private static final String COMPONENT_NAME = "java-aws-sdk";
  private static final ClassValue<String> OPERATION_NAME =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          String ret = type.getSimpleName();
          ret = ret.substring(0, ret.length() - 7); // remove 'Request'
          return ret;
        }
      };

  @Override
  protected void onStart(AttributesBuilder attributes, Request<?> request) {
    set(attributes, AWS_AGENT, COMPONENT_NAME);
    set(attributes, AWS_SERVICE, request.getServiceName());
    set(attributes, AWS_OPERATION, extractOperationName(request));
    set(attributes, AWS_ENDPOINT, request.getEndpoint().toString());

    Object originalRequest = request.getOriginalRequest();
    set(attributes, AWS_BUCKET_NAME, RequestAccess.getBucketName(originalRequest));
    set(attributes, AWS_QUEUE_URL, RequestAccess.getQueueUrl(originalRequest));
    set(attributes, AWS_QUEUE_NAME, RequestAccess.getQueueName(originalRequest));
    set(attributes, AWS_STREAM_NAME, RequestAccess.getStreamName(originalRequest));
    set(attributes, AWS_TABLE_NAME, RequestAccess.getTableName(originalRequest));
  }

  private static void set(AttributesBuilder attributes, AttributeKey<String> key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }

  private static String extractOperationName(Request<?> request) {
    return OPERATION_NAME.get(request.getOriginalRequest().getClass());
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {
    if (response != null && response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      AmazonWebServiceResponse<?> awsResp = (AmazonWebServiceResponse<?>) response.getAwsResponse();
      set(attributes, AWS_REQUEST_ID, awsResp.getRequestId());
    }
  }
}
