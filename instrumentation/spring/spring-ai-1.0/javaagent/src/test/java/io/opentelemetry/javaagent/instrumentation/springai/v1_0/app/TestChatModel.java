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
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

@SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
public class TestChatModel implements ChatModel {
  private SpanContext lastSpanContext;

  @Override
  public ChatResponse call(Prompt prompt) {
    lastSpanContext = Span.current().getSpanContext();
    return response();
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.defer(
        () -> {
          lastSpanContext = Span.current().getSpanContext();
          return Flux.just(response());
        });
  }

  public SpanContext getLastSpanContext() {
    return lastSpanContext;
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
