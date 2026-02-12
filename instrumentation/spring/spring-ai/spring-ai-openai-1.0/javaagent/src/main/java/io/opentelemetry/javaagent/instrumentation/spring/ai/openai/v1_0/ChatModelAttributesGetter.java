/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

enum ChatModelAttributesGetter
    implements GenAiAttributesGetter<ChatCompletionRequest, ChatCompletion> {
  INSTANCE;

  @Override
  public String getOperationName(ChatCompletionRequest request) {
    return GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
  }

  @Override
  public String getSystem(ChatCompletionRequest request) {
    return GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI;
  }

  @Nullable
  @Override
  public String getRequestModel(ChatCompletionRequest request) {
    return request.model();
  }

  @Nullable
  @Override
  public String getOperationTarget(ChatCompletionRequest request) {
    return getRequestModel(request);
  }

  @Nullable
  @Override
  public Long getRequestSeed(ChatCompletionRequest request) {
    if (request.seed() == null) {
      return null;
    }
    return Long.valueOf(request.seed());
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ChatCompletionRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ChatCompletionRequest request) {
    return request.frequencyPenalty();
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ChatCompletionRequest request) {
    if (request.maxTokens() == null && request.maxCompletionTokens() == null) {
      return null;
    }
    // Use maxCompletionTokens if available, otherwise fall back to maxTokens
    Integer maxTokens =
        request.maxCompletionTokens() != null ? request.maxCompletionTokens() : request.maxTokens();
    return maxTokens != null ? Long.valueOf(maxTokens) : null;
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ChatCompletionRequest request) {
    return request.presencePenalty();
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ChatCompletionRequest request) {
    if (request.stop() == null) {
      return null;
    }
    return request.stop();
  }

  @Nullable
  @Override
  public Double getRequestTemperature(ChatCompletionRequest request) {
    return request.temperature();
  }

  @Nullable
  @Override
  public Double getRequestTopK(ChatCompletionRequest request) {
    // OpenAI doesn't support top_k parameter
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(ChatCompletionRequest request) {
    return request.topP();
  }

  @Nullable
  @Override
  public Long getChoiceCount(ChatCompletionRequest request) {
    if (request.n() == null) {
      return null;
    }
    return Long.valueOf(request.n());
  }

  @Override
  public List<String> getResponseFinishReasons(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null || response.choices() == null) {
      return emptyList();
    }
    return response.choices().stream()
        .map(
            choice ->
                choice.finishReason() != null ? choice.finishReason().name().toLowerCase() : "")
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public String getResponseId(ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.id();
  }

  @Override
  @Nullable
  public String getResponseModel(ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.model();
  }

  @Override
  @Nullable
  public Long getUsageInputTokens(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null || response.usage() == null || response.usage().promptTokens() == null) {
      return null;
    }
    return Long.valueOf(response.usage().promptTokens());
  }

  @Override
  @Nullable
  public Long getUsageOutputTokens(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null
        || response.usage() == null
        || response.usage().completionTokens() == null) {
      return null;
    }
    return Long.valueOf(response.usage().completionTokens());
  }
}
