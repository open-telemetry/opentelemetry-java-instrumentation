/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

enum ChatAttributesGetter
    implements GenAiAttributesGetter<ChatCompletionCreateParams, ChatCompletion> {
  INSTANCE;

  @Override
  public String getOperationName(ChatCompletionCreateParams request) {
    return GenAiAttributes.GenAiOperationNameIncubatingValues.CHAT;
  }

  @Override
  public String getSystem(ChatCompletionCreateParams request) {
    return GenAiAttributes.GenAiSystemIncubatingValues.OPENAI;
  }

  @Override
  public String getRequestModel(ChatCompletionCreateParams request) {
    return request.model().asString();
  }

  @Nullable
  @Override
  public Long getRequestSeed(ChatCompletionCreateParams request) {
    return request.seed().orElse(null);
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ChatCompletionCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ChatCompletionCreateParams request) {
    return request.frequencyPenalty().orElse(null);
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ChatCompletionCreateParams request) {
    return request.maxCompletionTokens().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ChatCompletionCreateParams request) {
    return request.presencePenalty().orElse(null);
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ChatCompletionCreateParams request) {
    return request
        .stop()
        .map(
            s -> {
              if (s.isString()) {
                return singletonList(s.asString());
              }
              if (s.isStrings()) {
                return s.asStrings();
              }
              return null;
            })
        .orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestTemperature(ChatCompletionCreateParams request) {
    return request.temperature().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestTopK(ChatCompletionCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(ChatCompletionCreateParams request) {
    return request.topP().orElse(null);
  }

  @Override
  public List<String> getResponseFinishReasons(
      ChatCompletionCreateParams request, @Nullable ChatCompletion response) {
    if (response == null) {
      return emptyList();
    }
    return response.choices().stream()
        .map(choice -> choice.finishReason().asString())
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public String getResponseId(
      ChatCompletionCreateParams request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.id();
  }

  @Override
  @Nullable
  public String getResponseModel(
      ChatCompletionCreateParams request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.model();
  }

  @Override
  @Nullable
  public Long getUsageInputTokens(
      ChatCompletionCreateParams request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.usage().map(CompletionUsage::promptTokens).orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageOutputTokens(
      ChatCompletionCreateParams request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.usage().map(CompletionUsage::completionTokens).orElse(null);
  }
}
