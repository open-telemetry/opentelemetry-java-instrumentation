/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BEDROCK_RUNTIME_MODEL_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BEDROCK_SYSTEM;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_ENDPOINT;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_GUARDRAIL_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_GUARDRAIL_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_KNOWLEDGE_BASE_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_LAMBDA_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_LAMBDA_RESOURCE_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_SECRET_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STATE_MACHINE_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STEP_FUNCTIONS_ACTIVITY_ARN;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_TABLE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<Request<?>, Response<?>> {
  private static final String COMPONENT_NAME = "java-aws-sdk";
  private static final String BEDROCK_SERVICE = "AmazonBedrock";
  private static final String BEDROCK_AGENT_SERVICE = "AWSBedrockAgent";
  private static final String BEDROCK_AGENT_RUNTIME_SERVICE = "AWSBedrockAgentRuntime";
  private static final String BEDROCK_RUNTIME_SERVICE = "AmazonBedrockRuntime";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    attributes.put(AWS_AGENT, COMPONENT_NAME);
    attributes.put(AWS_ENDPOINT, request.getEndpoint().toString());

    Object originalRequest = request.getOriginalRequest();
    String requestClassName = originalRequest.getClass().getSimpleName();
    setAttribute(attributes, AWS_BUCKET_NAME, originalRequest, RequestAccess::getBucketName);
    setAttribute(attributes, AWS_QUEUE_URL, originalRequest, RequestAccess::getQueueUrl);
    setAttribute(attributes, AWS_QUEUE_NAME, originalRequest, RequestAccess::getQueueName);
    setAttribute(attributes, AWS_STREAM_NAME, originalRequest, RequestAccess::getStreamName);
    setAttribute(attributes, AWS_TABLE_NAME, originalRequest, RequestAccess::getTableName);
    setAttribute(
        attributes, AWS_STATE_MACHINE_ARN, originalRequest, RequestAccess::getStateMachineArn);
    setAttribute(
        attributes,
        AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
        originalRequest,
        RequestAccess::getStepFunctionsActivityArn);
    setAttribute(attributes, AWS_SNS_TOPIC_ARN, originalRequest, RequestAccess::getSnsTopicArn);
    setAttribute(attributes, AWS_SECRET_ARN, originalRequest, RequestAccess::getSecretArn);
    setAttribute(attributes, AWS_LAMBDA_NAME, originalRequest, RequestAccess::getLambdaName);
    setAttribute(
        attributes, AWS_LAMBDA_RESOURCE_ID, originalRequest, RequestAccess::getLambdaResourceId);

    // Get serviceName defined in the AWS Java SDK V1 Request class.
    String serviceName = request.getServiceName();
    // Extract request attributes only for Bedrock services.
    if (isBedrockService(serviceName)) {
      bedrockOnStart(attributes, originalRequest, requestClassName, serviceName);
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
      setAttribute(attributes, AWS_STATE_MACHINE_ARN, awsResp, RequestAccess::getStateMachineArn);
      setAttribute(
          attributes,
          AWS_STEP_FUNCTIONS_ACTIVITY_ARN,
          awsResp,
          RequestAccess::getStepFunctionsActivityArn);
      setAttribute(attributes, AWS_SNS_TOPIC_ARN, awsResp, RequestAccess::getSnsTopicArn);
      setAttribute(attributes, AWS_SECRET_ARN, awsResp, RequestAccess::getSecretArn);
      if (awsResp instanceof AmazonWebServiceResponse) {
        AmazonWebServiceResponse<?> awsWebServiceResponse = (AmazonWebServiceResponse<?>) awsResp;
        String requestId = awsWebServiceResponse.getRequestId();
        if (requestId != null) {
          attributes.put(AWS_REQUEST_ID, requestId);
        }
      }
      // Get serviceName defined in the AWS Java SDK V1 Request class.
      String serviceName = request.getServiceName();
      // Extract response attributes for Bedrock services
      if (awsResp != null && isBedrockService(serviceName)) {
        bedrockOnEnd(attributes, awsResp, serviceName);
      }
    }
  }

  private static void bedrockOnStart(
      AttributesBuilder attributes,
      Object originalRequest,
      String requestClassName,
      String serviceName) {
    switch (serviceName) {
      case BEDROCK_SERVICE:
        setAttribute(attributes, AWS_GUARDRAIL_ID, originalRequest, RequestAccess::getGuardrailId);
        break;
      case BEDROCK_AGENT_SERVICE:
        AwsBedrockResourceType resourceType =
            AwsBedrockResourceType.getRequestType(requestClassName);
        if (resourceType != null) {
          setAttribute(
              attributes,
              resourceType.getKeyAttribute(),
              originalRequest,
              resourceType.getAttributeValueAccessor());
        }
        break;
      case BEDROCK_AGENT_RUNTIME_SERVICE:
        setAttribute(attributes, AWS_AGENT_ID, originalRequest, RequestAccess::getAgentId);
        setAttribute(
            attributes, AWS_KNOWLEDGE_BASE_ID, originalRequest, RequestAccess::getKnowledgeBaseId);
        break;
      case BEDROCK_RUNTIME_SERVICE:
        if (!Objects.equals(requestClassName, "InvokeModelRequest")) {
          break;
        }
        attributes.put(AWS_BEDROCK_SYSTEM, "aws_bedrock");
        Function<Object, String> getter = RequestAccess::getModelId;
        String modelId = getter.apply(originalRequest);
        attributes.put(AWS_BEDROCK_RUNTIME_MODEL_ID, modelId);

        setAttribute(
            attributes, GEN_AI_REQUEST_MAX_TOKENS, originalRequest, RequestAccess::getMaxTokens);
        setAttribute(
            attributes, GEN_AI_REQUEST_TEMPERATURE, originalRequest, RequestAccess::getTemperature);
        setAttribute(attributes, GEN_AI_REQUEST_TOP_P, originalRequest, RequestAccess::getTopP);
        setAttribute(
            attributes, GEN_AI_USAGE_INPUT_TOKENS, originalRequest, RequestAccess::getInputTokens);
        break;
      default:
        break;
    }
  }

  private static void bedrockOnEnd(
      AttributesBuilder attributes, Object awsResp, String serviceName) {
    switch (serviceName) {
      case BEDROCK_SERVICE:
        setAttribute(attributes, AWS_GUARDRAIL_ID, awsResp, RequestAccess::getGuardrailId);
        setAttribute(attributes, AWS_GUARDRAIL_ARN, awsResp, RequestAccess::getGuardrailArn);
        break;
      case BEDROCK_AGENT_SERVICE:
        String responseClassName = awsResp.getClass().getSimpleName();
        AwsBedrockResourceType resourceType =
            AwsBedrockResourceType.getResponseType(responseClassName);
        if (resourceType != null) {
          setAttribute(
              attributes,
              resourceType.getKeyAttribute(),
              awsResp,
              resourceType.getAttributeValueAccessor());
        }
        break;
      case BEDROCK_AGENT_RUNTIME_SERVICE:
        setAttribute(attributes, AWS_AGENT_ID, awsResp, RequestAccess::getAgentId);
        setAttribute(attributes, AWS_KNOWLEDGE_BASE_ID, awsResp, RequestAccess::getKnowledgeBaseId);
        break;
      case BEDROCK_RUNTIME_SERVICE:
        if (!Objects.equals(awsResp.getClass().getSimpleName(), "InvokeModelResult")) {
          break;
        }

        setAttribute(attributes, GEN_AI_USAGE_INPUT_TOKENS, awsResp, RequestAccess::getInputTokens);
        setAttribute(
            attributes, GEN_AI_USAGE_OUTPUT_TOKENS, awsResp, RequestAccess::getOutputTokens);
        setAttribute(
            attributes, GEN_AI_RESPONSE_FINISH_REASONS, awsResp, RequestAccess::getFinishReasons);
        break;
      default:
        break;
    }
  }

  private static boolean isBedrockService(String serviceName) {
    // Check if the serviceName belongs to Bedrock Services defined in AWS Java SDK V1.
    // For example <a
    // href="https://github.com/aws/aws-sdk-java/blob/38031248a696468e19a4670c0c4585637d5e7cc6/aws-java-sdk-bedrock/src/main/java/com/amazonaws/services/bedrock/AmazonBedrock.java#L34">AmazonBedrock</a>
    return serviceName.equals(BEDROCK_SERVICE)
        || serviceName.equals(BEDROCK_AGENT_SERVICE)
        || serviceName.equals(BEDROCK_AGENT_RUNTIME_SERVICE)
        || serviceName.equals(BEDROCK_RUNTIME_SERVICE);
  }

  private static void setAttribute(
      AttributesBuilder attributes,
      AttributeKey<String> key,
      Object request,
      Function<Object, String> getter) {
    String value = getter.apply(request);
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
