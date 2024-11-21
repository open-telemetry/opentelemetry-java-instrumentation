/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

final class RequestAccess {

  private static final ClassValue<RequestAccess> REQUEST_ACCESSORS =
      new ClassValue<RequestAccess>() {
        @Override
        protected RequestAccess computeValue(Class<?> type) {
          return new RequestAccess(type);
        }
      };

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Nullable
  private static JsonNode parseTargetBody(ByteBuffer buffer) {
    try {
      byte[] bytes;
      // Create duplicate to avoid mutating the original buffer position
      ByteBuffer duplicate = buffer.duplicate();
      if (buffer.hasArray()) {
        bytes =
            Arrays.copyOfRange(
                duplicate.array(),
                duplicate.arrayOffset(),
                duplicate.arrayOffset() + duplicate.remaining());
      } else {
        bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
      }
      return objectMapper.readTree(bytes);
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static JsonNode getJsonBody(Object target) {
    if (target == null) {
      return null;
    }

    RequestAccess access = REQUEST_ACCESSORS.get(target.getClass());
    ByteBuffer bodyBuffer = invokeOrNullGeneric(access.getBody, target, ByteBuffer.class);
    if (bodyBuffer == null) {
      return null;
    }

    return parseTargetBody(bodyBuffer);
  }

  @Nullable
  private static String findFirstMatchingPath(JsonNode jsonBody, String... paths) {
    if (jsonBody == null) {
      return null;
    }

    return Stream.of(paths)
        .map(
            path -> {
              JsonNode node = jsonBody.at(path);
              if (node != null && !node.isMissingNode()) {
                return node.asText();
              }
              return null;
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  private static String approximateTokenCount(JsonNode jsonBody, String... textPaths) {
    if (jsonBody == null) {
      return null;
    }

    return Stream.of(textPaths)
        .map(
            path -> {
              JsonNode node = jsonBody.at(path);
              if (node != null && !node.isMissingNode()) {
                int tokenEstimate = (int) Math.ceil(node.asText().length() / 6.0);
                return Integer.toString(tokenEstimate);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/textGenerationConfig/maxTokenCount"
  // Anthropic Claude -> "/max_tokens"
  // Cohere Command -> "/max_tokens"
  // Cohere Command R -> "/max_tokens"
  // AI21 Jamba -> "/max_tokens"
  // Meta Llama -> "/max_gen_len"
  // Mistral AI -> "/max_tokens"
  @Nullable
  static String getMaxTokens(Object target) {
    return findFirstMatchingPath(
        getJsonBody(target), "/textGenerationConfig/maxTokenCount", "/max_tokens", "/max_gen_len");
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/textGenerationConfig/temperature"
  // Anthropic Claude -> "/temperature"
  // Cohere Command -> "/temperature"
  // Cohere Command R -> "/temperature"
  // AI21 Jamba -> "/temperature"
  // Meta Llama -> "/temperature"
  // Mistral AI -> "/temperature"
  @Nullable
  static String getTemperature(Object target) {
    return findFirstMatchingPath(
        getJsonBody(target), "/textGenerationConfig/temperature", "/temperature");
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/textGenerationConfig/topP"
  // Anthropic Claude -> "/top_p"
  // Cohere Command -> "/p"
  // Cohere Command R -> "/p"
  // AI21 Jamba -> "/top_p"
  // Meta Llama -> "/top_p"
  // Mistral AI -> "/top_p"
  @Nullable
  static String getTopP(Object target) {
    return findFirstMatchingPath(getJsonBody(target), "/textGenerationConfig/topP", "/top_p", "/p");
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/inputTextTokenCount"
  // Anthropic Claude -> "/usage/input_tokens"
  // Cohere Command -> "/prompt"
  // Cohere Command R -> "/message"
  // AI21 Jamba -> "/usage/prompt_tokens"
  // Meta Llama -> "/prompt_token_count"
  // Mistral AI -> "/prompt"
  @Nullable
  static String getInputTokens(Object target) {
    JsonNode jsonBody = getJsonBody(target);
    if (jsonBody == null) {
      return null;
    }

    // Try direct tokens counts first
    String directCount =
        findFirstMatchingPath(
            jsonBody,
            "/inputTextTokenCount",
            "/usage/input_tokens",
            "/usage/prompt_tokens",
            "/prompt_token_count");

    if (directCount != null) {
      return directCount;
    }

    // Fall back to token approximation
    return approximateTokenCount(jsonBody, "/prompt", "/message");
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/results/0/tokenCount"
  // Anthropic Claude -> "/usage/output_tokens"
  // Cohere Command -> "/generations/0/text"
  // Cohere Command R -> "/text"
  // AI21 Jamba -> "/usage/completion_tokens"
  // Meta Llama -> "/generation_token_count"
  // Mistral AI -> "/outputs/0/text"
  @Nullable
  static String getOutputTokens(Object target) {
    JsonNode jsonBody = getJsonBody(target);
    if (jsonBody == null) {
      return null;
    }

    // Try direct token counts first
    String directCount =
        findFirstMatchingPath(
            jsonBody,
            "/results/0/tokenCount",
            "/usage/output_tokens",
            "/usage/completion_tokens",
            "/generation_token_count");

    if (directCount != null) {
      return directCount;
    }

    return approximateTokenCount(jsonBody, "/outputs/0/text", "/text");
  }

  // Model -> Path Mapping:
  // Amazon Titan -> "/results/0/completionReason"
  // Anthropic Claude -> "/stop_reason"
  // Cohere Command -> "/generations/0/finish_reason"
  // Cohere Command R -> "/finish_reason"
  // AI21 Jamba -> "/choices/0/finish_reason"
  // Meta Llama -> "/stop_reason"
  // Mistral AI -> "/outputs/0/stop_reason"
  @Nullable
  static String getFinishReasons(Object target) {
    String finishReason =
        findFirstMatchingPath(
            getJsonBody(target),
            "/results/0/completionReason",
            "/stop_reason",
            "/generations/0/finish_reason",
            "/choices/0/finish_reason",
            "/outputs/0/stop_reason",
            "/finish_reason");

    return finishReason != null ? "[" + finishReason + "]" : null;
  }

  @Nullable
  static String getLambdaName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaName, request);
  }

  @Nullable
  static String getLambdaResourceId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaResourceId, request);
  }

  @Nullable
  static String getSecretArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSecretArn, request);
  }

  @Nullable
  static String getSnsTopicArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSnsTopicArn, request);
  }

  @Nullable
  static String getStepFunctionsActivityArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStepFunctionsActivityArn, request);
  }

  @Nullable
  static String getStateMachineArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStateMachineArn, request);
  }

  @Nullable
  static String getBucketName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getBucketName, request);
  }

  @Nullable
  static String getQueueUrl(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getQueueUrl, request);
  }

  @Nullable
  static String getQueueName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getQueueName, request);
  }

  @Nullable
  static String getStreamName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStreamName, request);
  }

  @Nullable
  static String getTableName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTableName, request);
  }

  @Nullable
  static String getAgentId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getAgentId, request);
  }

  @Nullable
  static String getKnowledgeBaseId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getKnowledgeBaseId, request);
  }

  @Nullable
  static String getDataSourceId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getDataSourceId, request);
  }

  @Nullable
  static String getGuardrailId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getGuardrailId, request);
  }

  @Nullable
  static String getGuardrailArn(Object request) {
    if (request == null) {
      return null;
    }
    return findNestedAccessorOrNull(request, "getGuardrailArn");
  }

  @Nullable
  static String getModelId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getModelId, request);
  }

  @Nullable
  private static String invokeOrNull(@Nullable MethodHandle method, Object obj) {
    if (method == null) {
      return null;
    }
    try {
      return (String) method.invoke(obj);
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static <T> T invokeOrNullGeneric(
      @Nullable MethodHandle method, Object obj, Class<T> returnType) {
    if (method == null) {
      return null;
    }
    try {
      return returnType.cast(method.invoke(obj));
    } catch (Throwable e) {
      return null;
    }
  }

  @Nullable private final MethodHandle getBucketName;
  @Nullable private final MethodHandle getQueueUrl;
  @Nullable private final MethodHandle getQueueName;
  @Nullable private final MethodHandle getStreamName;
  @Nullable private final MethodHandle getTableName;
  @Nullable private final MethodHandle getAgentId;
  @Nullable private final MethodHandle getKnowledgeBaseId;
  @Nullable private final MethodHandle getDataSourceId;
  @Nullable private final MethodHandle getGuardrailId;
  @Nullable private final MethodHandle getModelId;
  @Nullable private final MethodHandle getBody;
  @Nullable private final MethodHandle getStateMachineArn;
  @Nullable private final MethodHandle getStepFunctionsActivityArn;
  @Nullable private final MethodHandle getSnsTopicArn;
  @Nullable private final MethodHandle getSecretArn;
  @Nullable private final MethodHandle getLambdaName;
  @Nullable private final MethodHandle getLambdaResourceId;

  private RequestAccess(Class<?> clz) {
    getBucketName = findAccessorOrNull(clz, "getBucketName", String.class);
    getQueueUrl = findAccessorOrNull(clz, "getQueueUrl", String.class);
    getQueueName = findAccessorOrNull(clz, "getQueueName", String.class);
    getStreamName = findAccessorOrNull(clz, "getStreamName", String.class);
    getTableName = findAccessorOrNull(clz, "getTableName", String.class);
    getAgentId = findAccessorOrNull(clz, "getAgentId", String.class);
    getKnowledgeBaseId = findAccessorOrNull(clz, "getKnowledgeBaseId", String.class);
    getDataSourceId = findAccessorOrNull(clz, "getDataSourceId", String.class);
    getGuardrailId = findAccessorOrNull(clz, "getGuardrailId", String.class);
    getModelId = findAccessorOrNull(clz, "getModelId", String.class);
    getBody = findAccessorOrNull(clz, "getBody", ByteBuffer.class);
    getStateMachineArn = findAccessorOrNull(clz, "getStateMachineArn", String.class);
    getStepFunctionsActivityArn = findAccessorOrNull(clz, "getActivityArn", String.class);
    getSnsTopicArn = findAccessorOrNull(clz, "getTopicArn", String.class);
    getSecretArn = findAccessorOrNull(clz, "getARN", String.class);
    getLambdaName = findAccessorOrNull(clz, "getFunctionName", String.class);
    getLambdaResourceId = findAccessorOrNull(clz, "getUUID", String.class);
  }

  @Nullable
  private static MethodHandle findAccessorOrNull(
      Class<?> clz, String methodName, Class<?> returnType) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(clz, methodName, MethodType.methodType(returnType));
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static String findNestedAccessorOrNull(Object obj, String... methodNames) {
    Object current = obj;
    for (String methodName : methodNames) {
      if (current == null) {
        return null;
      }
      try {
        Method method = current.getClass().getMethod(methodName);
        current = method.invoke(current);
      } catch (Exception e) {
        return null;
      }
    }
    return (current instanceof String) ? (String) current : null;
  }
}
