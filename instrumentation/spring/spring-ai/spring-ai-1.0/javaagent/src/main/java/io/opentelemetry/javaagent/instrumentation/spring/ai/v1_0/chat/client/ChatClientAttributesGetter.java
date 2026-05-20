/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAgentAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

public enum ChatClientAttributesGetter
    implements
        GenAiAttributesGetter<ChatClientRequest, ChatClientResponse>,
        GenAiAgentAttributesGetter<ChatClientRequest, ChatClientResponse> {
  INSTANCE;

  @Override
  public String getOperationName(ChatClientRequest request) {
    return "invoke_agent";
  }

  @Override
  public String getSystem(ChatClientRequest request) {
    return "spring-ai";
  }

  @Nullable
  @Override
  public String getRequestModel(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getModel();
  }

  @Override
  public String getOperationTarget(ChatClientRequest request) {
    return getName(request);
  }

  @Nullable
  @Override
  public Long getRequestSeed(ChatClientRequest request) {
    // Spring AI currently does not support seed parameter
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ChatClientRequest request) {
    // Spring AI currently does not support encoding_formats parameter
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getFrequencyPenalty();
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ChatClientRequest request) {
    if (request.prompt().getOptions() == null
        || request.prompt().getOptions().getMaxTokens() == null) {
      return null;
    }
    return request.prompt().getOptions().getMaxTokens().longValue();
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getPresencePenalty();
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getStopSequences();
  }

  @Nullable
  @Override
  public Double getRequestTemperature(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getTemperature();
  }

  @Nullable
  @Override
  public Double getRequestTopK(ChatClientRequest request) {
    if (request.prompt().getOptions() == null || request.prompt().getOptions().getTopK() == null) {
      return null;
    }
    return request.prompt().getOptions().getTopK().doubleValue();
  }

  @Nullable
  @Override
  public Double getRequestTopP(ChatClientRequest request) {
    if (request.prompt().getOptions() == null) {
      return null;
    }
    return request.prompt().getOptions().getTopP();
  }

  @Override
  public List<String> getResponseFinishReasons(
      ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (response == null
        || response.chatResponse() == null
        || response.chatResponse().getResult() == null
        || response.chatResponse().getResult().getMetadata() == null
        || response.chatResponse().getResult().getMetadata().getFinishReason() == null) {
      return emptyList();
    }

    return singletonList(
        response.chatResponse().getResult().getMetadata().getFinishReason().toLowerCase());
  }

  @Nullable
  @Override
  public String getResponseId(ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (response == null
        || response.chatResponse() == null
        || response.chatResponse().getMetadata() == null) {
      return null;
    }

    return response.chatResponse().getMetadata().getId();
  }

  @Nullable
  @Override
  public String getResponseModel(ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (response == null
        || response.chatResponse() == null
        || response.chatResponse().getMetadata() == null
        || response.chatResponse().getMetadata().getModel() == null
        || response.chatResponse().getMetadata().getModel().isEmpty()) {
      return null;
    }

    return response.chatResponse().getMetadata().getModel();
  }

  @Nullable
  @Override
  public Long getUsageInputTokens(
      ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (response == null
        || response.chatResponse() == null
        || response.chatResponse().getMetadata() == null
        || response.chatResponse().getMetadata().getUsage() == null
        || response.chatResponse().getMetadata().getUsage().getPromptTokens() == null
        || response.chatResponse().getMetadata().getUsage().getPromptTokens() == 0) {
      return null;
    }

    return response.chatResponse().getMetadata().getUsage().getPromptTokens().longValue();
  }

  @Nullable
  @Override
  public Long getUsageOutputTokens(
      ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (response == null
        || response.chatResponse() == null
        || response.chatResponse().getMetadata() == null
        || response.chatResponse().getMetadata().getUsage() == null
        || response.chatResponse().getMetadata().getUsage().getCompletionTokens() == null
        || response.chatResponse().getMetadata().getUsage().getCompletionTokens() == 0) {
      return null;
    }

    return response.chatResponse().getMetadata().getUsage().getCompletionTokens().longValue();
  }

  @Override
  public String getName(ChatClientRequest request) {
    return "spring_ai chat_client";
  }

  @Nullable
  @Override
  public String getDescription(ChatClientRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getId(ChatClientRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDataSourceId(ChatClientRequest request) {
    return null;
  }
}
