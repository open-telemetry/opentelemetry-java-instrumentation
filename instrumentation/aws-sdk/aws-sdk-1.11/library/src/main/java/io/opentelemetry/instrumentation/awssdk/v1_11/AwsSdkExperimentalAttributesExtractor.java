/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsSdkExperimentalAttributesExtractor
    extends AttributesExtractor<Request<?>, Response<?>> {
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
    attributes.put("aws.agent", COMPONENT_NAME);
    attributes.put("aws.service", request.getServiceName());
    attributes.put("aws.operation", extractOperationName(request));
    attributes.put("aws.endpoint", request.getEndpoint().toString());

    Object originalRequest = request.getOriginalRequest();
    String bucketName = RequestAccess.getBucketName(originalRequest);
    if (bucketName != null) {
      attributes.put("aws.bucket.name", bucketName);
    }
    String queueUrl = RequestAccess.getQueueUrl(originalRequest);
    if (queueUrl != null) {
      attributes.put("aws.queue.url", queueUrl);
    }
    String queueName = RequestAccess.getQueueName(originalRequest);
    if (queueName != null) {
      attributes.put("aws.queue.name", queueName);
    }
    String streamName = RequestAccess.getStreamName(originalRequest);
    if (streamName != null) {
      attributes.put("aws.stream.name", streamName);
    }
    String tableName = RequestAccess.getTableName(originalRequest);
    if (tableName != null) {
      attributes.put("aws.table.name", tableName);
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
      attributes.put("aws.requestId", awsResp.getRequestId());
    }
  }
}
