/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0.app;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

@SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
public class TestChatModel implements ChatModel {
  private SpanContext lastSpanContext;
  private final ChatOptions defaultOptions;
  private RuntimeException callFailure;
  private Flux<ChatResponse> streamPublisher;

  public TestChatModel() {
    this(null);
  }

  public TestChatModel(ChatOptions defaultOptions) {
    this.defaultOptions = defaultOptions;
    this.streamPublisher = Flux.just(response());
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    lastSpanContext = Span.current().getSpanContext();
    if (callFailure != null) {
      throw callFailure;
    }
    return response();
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.defer(
        () -> {
          lastSpanContext = Span.current().getSpanContext();
          return streamPublisher;
        });
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return defaultOptions;
  }

  public SpanContext getLastSpanContext() {
    return lastSpanContext;
  }

  public void setCallFailure(RuntimeException callFailure) {
    this.callFailure = callFailure;
  }

  public void setStreamPublisher(Flux<ChatResponse> streamPublisher) {
    this.streamPublisher = streamPublisher;
  }

  private static ChatResponse response() {
    Generation generation =
        new Generation(
            new AssistantMessage("A trace represents an end-to-end request."),
            ChatGenerationMetadata.builder().finishReason("stop").build());
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .id("response-id")
            .model("test-model")
            .usage(new DefaultUsage(3, 2))
            .build();
    return new ChatResponse(singletonList(generation), metadata);
  }
}
