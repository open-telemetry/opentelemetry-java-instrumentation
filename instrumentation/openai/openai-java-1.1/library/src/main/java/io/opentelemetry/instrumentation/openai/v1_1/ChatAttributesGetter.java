/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class ChatAttributesGetter
    implements GenAiAttributesGetter<ChatCompletionRequest, ChatCompletion> {

  ChatAttributesGetter() {}

  @Override
  public String getOperationName(ChatCompletionRequest request) {
    return GenAiAttributes.GenAiOperationNameIncubatingValues.CHAT;
  }

  @Override
  public String getSystem(ChatCompletionRequest request) {
    return GenAiAttributes.GenAiProviderNameIncubatingValues.OPENAI;
  }

  @Override
  public String getRequestModel(ChatCompletionRequest request) {
    return request.getRequest().model().asString();
  }

  @Override
  public boolean isRequestStreaming(ChatCompletionRequest request) {
    return request.isStreaming();
  }

  @Nullable
  @Override
  public Long getRequestSeed(ChatCompletionRequest request) {
    return request.getRequest().seed().orElse(null);
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ChatCompletionRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ChatCompletionRequest request) {
    return request.getRequest().frequencyPenalty().orElse(null);
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ChatCompletionRequest request) {
    return request.getRequest().maxCompletionTokens().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ChatCompletionRequest request) {
    return request.getRequest().presencePenalty().orElse(null);
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ChatCompletionRequest request) {
    return request
        .getRequest()
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
  public Double getRequestTemperature(ChatCompletionRequest request) {
    return request.getRequest().temperature().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestTopK(ChatCompletionRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(ChatCompletionRequest request) {
    return request.getRequest().topP().orElse(null);
  }

  @Override
  public List<String> getResponseFinishReasons(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null) {
      return emptyList();
    }
    return response.choices().stream()
        .map(choice -> choice.finishReason().asString())
        .collect(toList());
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
    if (response == null) {
      return null;
    }
    return response.usage().map(CompletionUsage::promptTokens).orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageOutputTokens(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (response == null) {
      return null;
    }
    return response.usage().map(CompletionUsage::completionTokens).orElse(null);
  }
}
