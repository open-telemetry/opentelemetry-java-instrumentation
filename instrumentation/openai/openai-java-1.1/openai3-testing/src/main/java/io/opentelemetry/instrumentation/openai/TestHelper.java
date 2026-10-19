/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
import java.util.concurrent.CompletableFuture;

public interface TestHelper {

  String id(ChatCompletionMessageToolCall toolCall);

  String arguments(ChatCompletionMessageToolCall toolCall);

  ChatCompletionTool chatCompletionTool(FunctionDefinition functionDefinition);

  MessageToolCallBuilder messageToolCallBuilder();

  MessageToolCallBuilder.FunctionBuilder messageToolCallFunctionBuilder();

  // The methods below call ResponseService overloads that SDK 2.x added, so they are only
  // supported when testing latest deps. With explicitOptions, they call the overload that also
  // takes RequestOptions.

  /** Calls {@code create} with structured-output params and returns the raw response. */
  Response createStructured(
      ResponseService service, ResponseCreateParams params, boolean explicitOptions);

  CompletableFuture<Response> createStructured(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions);

  StreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseService service, ResponseCreateParams params, boolean explicitOptions);

  AsyncStreamResponse<ResponseStreamEvent> createStructuredStreaming(
      ResponseServiceAsync service, ResponseCreateParams params, boolean explicitOptions);

  /** Calls {@code create()} without params. */
  Response createWithoutParams(ResponseService service, boolean explicitOptions);

  CompletableFuture<Response> createWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions);

  /** Calls {@code createStreaming()} without params. */
  StreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseService service, boolean explicitOptions);

  AsyncStreamResponse<ResponseStreamEvent> createStreamingWithoutParams(
      ResponseServiceAsync service, boolean explicitOptions);

  // The withoutRetries methods return {@code withOptions(...)} with retries disabled.

  OpenAIClient withoutRetries(OpenAIClient client);

  OpenAIClientAsync withoutRetries(OpenAIClientAsync client);

  ChatService withoutRetries(ChatService service);

  ChatServiceAsync withoutRetries(ChatServiceAsync service);

  ChatCompletionService withoutRetries(ChatCompletionService service);

  ChatCompletionServiceAsync withoutRetries(ChatCompletionServiceAsync service);

  EmbeddingService withoutRetries(EmbeddingService service);

  EmbeddingServiceAsync withoutRetries(EmbeddingServiceAsync service);

  ResponseService withoutRetries(ResponseService service);

  ResponseServiceAsync withoutRetries(ResponseServiceAsync service);

  interface MessageToolCallBuilder {
    @CanIgnoreReturnValue
    MessageToolCallBuilder id(String id);

    @CanIgnoreReturnValue
    MessageToolCallBuilder function(FunctionBuilder functionBuilder);

    ChatCompletionMessageToolCall build();

    interface FunctionBuilder {
      @CanIgnoreReturnValue
      FunctionBuilder name(String name);

      @CanIgnoreReturnValue
      FunctionBuilder arguments(String arguments);
    }
  }
}
