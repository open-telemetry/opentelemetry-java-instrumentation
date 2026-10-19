/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.EmbeddingServiceAsync;
import com.openai.services.async.ResponseServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.EmbeddingService;
import com.openai.services.blocking.ResponseService;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.opentelemetry.instrumentation.openai.TestHelper;
import io.opentelemetry.instrumentation.openai.TestHelper.MessageToolCallBuilder.FunctionBuilder;
import java.util.concurrent.CompletableFuture;

class OpenAi1TestHelper implements TestHelper {
  @Override
  public String id(ChatCompletionMessageToolCall toolCall) {
    return toolCall.id();
  }

  @Override
  public String arguments(ChatCompletionMessageToolCall toolCall) {
    return toolCall.function().arguments();
  }

  @Override
  public ChatCompletionTool chatCompletionTool(FunctionDefinition functionDefinition) {
    return ChatCompletionTool.builder().function(functionDefinition).build();
  }

  @Override
  public MessageToolCallBuilder messageToolCallBuilder() {
    return new MessageToolCallBuilder() {
      private final ChatCompletionMessageToolCall.Builder builder =
          ChatCompletionMessageToolCall.builder();

      @Override
      public MessageToolCallBuilder id(String id) {
        builder.id(id);
        return this;
      }

      @Override
      public MessageToolCallBuilder function(FunctionBuilder functionBuilder) {
        builder.function(((FunctionBuilderImpl) functionBuilder).builder.build());
        return this;
      }

      @Override
      public ChatCompletionMessageToolCall build() {
        return builder.build();
      }
    };
  }

  @Override
  public FunctionBuilder messageToolCallFunctionBuilder() {
    return new FunctionBuilderImpl();
  }

  private static class FunctionBuilderImpl implements FunctionBuilder {
    private final ChatCompletionMessageToolCall.Function.Builder builder =
        ChatCompletionMessageToolCall.Function.builder();

    @Override
    public FunctionBuilder name(String name) {
      builder.name(name);
      return this;
    }

    @Override
    public FunctionBuilder arguments(String arguments) {
      builder.arguments(arguments);
      return this;
    }
  }

  @Override
  public Response createStructured(
      ResponseService service, ResponseCreateParams params, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Response> createStructured(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseService service, ResponseCreateParams params, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AsyncStreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Response createWithoutParams(ResponseService service, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Response> createWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseService service, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AsyncStreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OpenAIClient withoutRetries(OpenAIClient client) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OpenAIClientAsync withoutRetries(OpenAIClientAsync client) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChatService withoutRetries(ChatService service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChatServiceAsync withoutRetries(ChatServiceAsync service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChatCompletionService withoutRetries(ChatCompletionService service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChatCompletionServiceAsync withoutRetries(ChatCompletionServiceAsync service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EmbeddingService withoutRetries(EmbeddingService service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EmbeddingServiceAsync withoutRetries(EmbeddingServiceAsync service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ResponseService withoutRetries(ResponseService service) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ResponseServiceAsync withoutRetries(ResponseServiceAsync service) {
    throw new UnsupportedOperationException();
  }
}
