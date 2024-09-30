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

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.ResponseMetadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.function.Function;
import javax.annotation.Nullable;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<Request<?>, Response<?>> {
  private static final String COMPONENT_NAME = "java-aws-sdk";
  private static final boolean CAN_GET_RESPONSE_METADATA = canGetResponseMetadata();

  // AmazonWebServiceResult is only available in v1.11.33 and later
  private static boolean canGetResponseMetadata() {
    try {
      Class<?> clazz = Class.forName("com.amazonaws.AmazonWebServiceResult");
      clazz.getMethod("getSdkResponseMetadata");
      return true;
    } catch (ClassNotFoundException | NoSuchMethodException exception) {
      return false;
    }
  }

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
    ResponseMetadata responseMetadata = getResponseMetadata(response);

    if (responseMetadata != null) {
      String requestId = responseMetadata.getRequestId();
      if (requestId != null) {
        attributes.put(AWS_REQUEST_ID, requestId);
      }
    }
  }

  @NoMuzzle
  private static ResponseMetadata getResponseMetadata(Response<?> response) {
    if (CAN_GET_RESPONSE_METADATA
        && response != null
        && response.getAwsResponse() instanceof AmazonWebServiceResult) {
      AmazonWebServiceResult<?> awsResp = (AmazonWebServiceResult<?>) response.getAwsResponse();
      return awsResp.getSdkResponseMetadata();
    }
    return null;
  }
}
