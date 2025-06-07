/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_LAMBDA_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_LAMBDA_RESOURCE_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_SECRET_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STATE_MACHINE_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STEP_FUNCTIONS_ACTIVITY_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_TABLE_NAME;

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

    Object originalRequest = request.getOriginalRequest();
    setRequestAttribute(attributes, AWS_BUCKET_NAME, originalRequest, RequestAccess::getBucketName);
    setRequestAttribute(attributes, AWS_QUEUE_URL, originalRequest, RequestAccess::getQueueUrl);
    setRequestAttribute(attributes, AWS_QUEUE_NAME, originalRequest, RequestAccess::getQueueName);
    setRequestAttribute(attributes, AWS_STREAM_NAME, originalRequest, RequestAccess::getStreamName);
    setRequestAttribute(attributes, AWS_TABLE_NAME, originalRequest, RequestAccess::getTableName);
    setRequestAttribute(
        attributes, AWS_STATE_MACHINE_ARN, originalRequest, RequestAccess::getStateMachineArn);
    setRequestAttribute(
        attributes,
        AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
        originalRequest,
        RequestAccess::getStepFunctionsActivityArn);
    setRequestAttribute(attributes, AWS_SNS_TOPIC_ARN, originalRequest, RequestAccess::getTopicArn);
    setRequestAttribute(attributes, AWS_SECRET_ARN, originalRequest, RequestAccess::getSecretArn);
    setRequestAttribute(attributes, AWS_LAMBDA_NAME, originalRequest, RequestAccess::getLambdaName);
    setRequestAttribute(
        attributes, AWS_LAMBDA_RESOURCE_ID, originalRequest, RequestAccess::getLambdaResourceId);
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
    if (response != null) {
      Object awsResp = response.getAwsResponse();
      setRequestAttribute(
          attributes, AWS_STATE_MACHINE_ARN, awsResp, RequestAccess::getStateMachineArn);
      setRequestAttribute(
          attributes,
          AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
          awsResp,
          RequestAccess::getStepFunctionsActivityArn);
      setRequestAttribute(attributes, AWS_SNS_TOPIC_ARN, awsResp, RequestAccess::getTopicArn);
      setRequestAttribute(attributes, AWS_SECRET_ARN, awsResp, RequestAccess::getSecretArn);
    }
  }
}
