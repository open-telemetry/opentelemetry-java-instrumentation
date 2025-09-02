/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

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

class AwsSdkAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {
  private static final boolean CAN_GET_RESPONSE_METADATA = canGetResponseMetadata();
  private static final AttributeKey<String> AWS_REQUEST_ID = stringKey("aws.request_id");

  // Copied from AwsIncubatingAttributes
  private static final AttributeKey<String> AWS_SECRETSMANAGER_SECRET_ARN =
      stringKey("aws.secretsmanager.secret.arn");
  private static final AttributeKey<String> AWS_LAMBDA_RESOURCE_MAPPING_ID =
      stringKey("aws.lambda.resource_mapping.id");
  private static final AttributeKey<String> AWS_SNS_TOPIC_ARN = stringKey("aws.sns.topic.arn");
  private static final AttributeKey<String> AWS_STEP_FUNCTIONS_ACTIVITY_ARN =
      stringKey("aws.step_functions.activity.arn");
  private static final AttributeKey<String> AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN =
      stringKey("aws.step_functions.state_machine.arn");

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
    Object originalRequest = request.getOriginalRequest();
    setAttribute(
        attributes,
        AWS_LAMBDA_RESOURCE_MAPPING_ID,
        originalRequest,
        RequestAccess::getLambdaResourceMappingId);
    setAttribute(attributes, AWS_SNS_TOPIC_ARN, originalRequest, RequestAccess::getSnsTopicArn);
    setAttribute(
        attributes,
        AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN,
        originalRequest,
        RequestAccess::getStateMachineArn);
    setAttribute(
        attributes,
        AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
        originalRequest,
        RequestAccess::getStepFunctionsActivityArn);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {
    Object awsResp = getAwsResponse(response);
    if (awsResp != null) {
      setAttribute(attributes, AWS_SECRETSMANAGER_SECRET_ARN, awsResp, RequestAccess::getSecretArn);
      setAttribute(
          attributes,
          AWS_LAMBDA_RESOURCE_MAPPING_ID,
          awsResp,
          RequestAccess::getLambdaResourceMappingId);
      setAttribute(attributes, AWS_SNS_TOPIC_ARN, awsResp, RequestAccess::getSnsTopicArn);
      setAttribute(
          attributes,
          AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN,
          awsResp,
          RequestAccess::getStateMachineArn);
      setAttribute(
          attributes,
          AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
          awsResp,
          RequestAccess::getStepFunctionsActivityArn);
    }

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

  public static void setAttribute(
      AttributesBuilder attributes,
      AttributeKey<String> key,
      Object carrier,
      Function<Object, String> getter) {
    String value = getter.apply(carrier);
    if (value != null) {
      attributes.put(key, value);
    }
  }

  private static Object getAwsResponse(Response<?> response) {
    if (response == null) {
      return null;
    }
    return response.getAwsResponse();
  }
}
