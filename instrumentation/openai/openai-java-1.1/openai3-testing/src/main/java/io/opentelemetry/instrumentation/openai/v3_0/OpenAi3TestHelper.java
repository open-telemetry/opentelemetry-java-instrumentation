/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
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

public class OpenAi3TestHelper implements TestHelper {
  @Override
  public String id(ChatCompletionMessageToolCall toolCall) {
    return toolCall.function().get().id();
  }

  @Override
  public String arguments(ChatCompletionMessageToolCall toolCall) {
    return toolCall.function().get().function().arguments();
  }

  @Override
  public ChatCompletionTool chatCompletionTool(FunctionDefinition functionDefinition) {
    return ChatCompletionTool.ofFunction(
        ChatCompletionFunctionTool.builder().function(functionDefinition).build());
  }

  @Override
  public MessageToolCallBuilder messageToolCallBuilder() {
    return new MessageToolCallBuilder() {
      private final ChatCompletionMessageFunctionToolCall.Builder builder =
          ChatCompletionMessageFunctionToolCall.builder();

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
        return ChatCompletionMessageToolCall.ofFunction(builder.build());
      }
    };
  }

  @Override
  public FunctionBuilder messageToolCallFunctionBuilder() {
    return new FunctionBuilderImpl();
  }

  private static class FunctionBuilderImpl implements FunctionBuilder {
    private final ChatCompletionMessageFunctionToolCall.Function.Builder builder =
        ChatCompletionMessageFunctionToolCall.Function.builder();

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
    StructuredResponse<Answer> response =
        explicitOptions
            ? service.create(structured(params), RequestOptions.none())
            : service.create(structured(params));
    return rawResponse(response);
  }

  @Override
  public CompletableFuture<Response> createStructured(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions) {
    CompletableFuture<StructuredResponse<Answer>> response =
        explicitOptions
            ? service.create(structured(params), RequestOptions.none())
            : service.create(structured(params));
    return response.thenApply(OpenAi3TestHelper::rawResponse);
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseService service, ResponseCreateParams params, boolean explicitOptions) {
    return explicitOptions
        ? service.createStreaming(structured(params), RequestOptions.none())
        : service.createStreaming(structured(params));
  }

  @Override
  public AsyncStreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions) {
    return explicitOptions
        ? service.createStreaming(structured(params), RequestOptions.none())
        : service.createStreaming(structured(params));
  }

  @Override
  public Response createWithoutParams(ResponseService service, boolean explicitOptions) {
    return explicitOptions ? service.create(RequestOptions.none()) : service.create();
  }

  @Override
  public CompletableFuture<Response> createWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions) {
    return explicitOptions ? service.create(RequestOptions.none()) : service.create();
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseService service, boolean explicitOptions) {
    return explicitOptions
        ? service.createStreaming(RequestOptions.none())
        : service.createStreaming();
  }

  @Override
  public AsyncStreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions) {
    return explicitOptions
        ? service.createStreaming(RequestOptions.none())
        : service.createStreaming();
  }

  @Override
  public OpenAIClient withoutRetries(OpenAIClient client) {
    return client.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public OpenAIClientAsync withoutRetries(OpenAIClientAsync client) {
    return client.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ChatService withoutRetries(ChatService service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ChatServiceAsync withoutRetries(ChatServiceAsync service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ChatCompletionService withoutRetries(ChatCompletionService service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ChatCompletionServiceAsync withoutRetries(ChatCompletionServiceAsync service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public EmbeddingService withoutRetries(EmbeddingService service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public EmbeddingServiceAsync withoutRetries(EmbeddingServiceAsync service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ResponseService withoutRetries(ResponseService service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  @Override
  public ResponseServiceAsync withoutRetries(ResponseServiceAsync service) {
    return service.withOptions(options -> options.maxRetries(0));
  }

  private static StructuredResponseCreateParams<Answer> structured(ResponseCreateParams params) {
    return params.toBuilder().text(Answer.class).build();
  }

  private static Response rawResponse(StructuredResponse<Answer> response) {
    Answer answer = response.output().get(0).asMessage().content().get(0).asOutputText();
    assertThat(answer.ocean).isEqualTo("Atlantic Ocean");
    return response.rawResponse();
  }

  /** The structured output the tests request. */
  public static class Answer {
    public String ocean;
  }
}
