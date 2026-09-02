/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class SpringAiAttributesGetter implements GenAiAttributesGetter<SpringAiRequest, ChatResponse> {
  @Override
  public String getOperationName(SpringAiRequest request) {
    return "chat";
  }

  @Override
  public String getSystem(SpringAiRequest request) {
    return request.provider();
  }

  @Override
  @Nullable
  public String getRequestModel(SpringAiRequest request) {
    return request.model();
  }

  @Override
  public boolean isRequestStreaming(SpringAiRequest request) {
    return request.streaming();
  }

  @Override
  public Long getRequestSeed(SpringAiRequest request) {
    return null;
  }

  @Override
  @Nullable
  public List<String> getRequestEncodingFormats(SpringAiRequest request) {
    return null;
  }

  @Override
  public Double getRequestFrequencyPenalty(SpringAiRequest request) {
    return request.frequencyPenalty();
  }

  @Override
  public Long getRequestMaxTokens(SpringAiRequest request) {
    Integer maxTokens = request.maxTokens();
    return maxTokens == null ? null : maxTokens.longValue();
  }

  @Override
  public Double getRequestPresencePenalty(SpringAiRequest request) {
    return request.presencePenalty();
  }

  @Override
  @Nullable
  public List<String> getRequestStopSequences(SpringAiRequest request) {
    return request.stopSequences();
  }

  @Override
  public Double getRequestTemperature(SpringAiRequest request) {
    return request.temperature();
  }

  @Override
  public Double getRequestTopK(SpringAiRequest request) {
    Integer topK = request.topK();
    return topK == null ? null : topK.doubleValue();
  }

  @Override
  public Double getRequestTopP(SpringAiRequest request) {
    return request.topP();
  }

  @Override
  public List<String> getResponseFinishReasons(
      SpringAiRequest request, @Nullable ChatResponse response) {
    if (response == null) {
      return emptyList();
    }
    return response.getResults().stream()
        .map(Generation::getMetadata)
        .filter(metadata -> metadata != null)
        .map(ChatGenerationMetadata::getFinishReason)
        .filter(reason -> reason != null)
        .collect(toList());
  }

  @Override
  @Nullable
  public String getResponseId(SpringAiRequest request, @Nullable ChatResponse response) {
    ChatResponseMetadata metadata = metadata(response);
    String id = metadata == null ? null : metadata.getId();
    return id == null || id.isEmpty() ? null : id;
  }

  @Override
  @Nullable
  public String getResponseModel(SpringAiRequest request, @Nullable ChatResponse response) {
    ChatResponseMetadata metadata = metadata(response);
    String model = metadata == null ? null : metadata.getModel();
    return model == null || model.isEmpty() ? null : model;
  }

  @Override
  @Nullable
  public Long getUsageInputTokens(SpringAiRequest request, @Nullable ChatResponse response) {
    Usage usage = usage(response);
    Integer tokens = usage == null ? null : usage.getPromptTokens();
    return tokens == null ? null : tokens.longValue();
  }

  @Override
  @Nullable
  public Long getUsageOutputTokens(SpringAiRequest request, @Nullable ChatResponse response) {
    Usage usage = usage(response);
    Integer tokens = usage == null ? null : usage.getCompletionTokens();
    return tokens == null ? null : tokens.longValue();
  }

  @Nullable
  private static ChatResponseMetadata metadata(@Nullable ChatResponse response) {
    return response == null ? null : response.getMetadata();
  }

  @Nullable
  private static Usage usage(@Nullable ChatResponse response) {
    ChatResponseMetadata metadata = metadata(response);
    Usage usage = metadata == null ? null : metadata.getUsage();
    return usage instanceof EmptyUsage ? null : usage;
  }
}
