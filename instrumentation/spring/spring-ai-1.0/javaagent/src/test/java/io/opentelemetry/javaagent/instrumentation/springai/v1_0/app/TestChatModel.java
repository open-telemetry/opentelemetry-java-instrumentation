/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0.app;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

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

public class TestChatModel implements ChatModel {
  private SpanContext lastSpanContext;
  private final ChatOptions defaultOptions;
  private RuntimeException defaultOptionsFailure;
  private RuntimeException callFailure;
  private RuntimeException streamFailure;
  private ChatResponse callResponse;
  private TestChatModel callDelegate;
  private TestChatModel callStreamDelegate;
  private Flux<ChatResponse> streamPublisher;
  private TestChatModel streamDelegate;
  private TestChatModel deferredStreamDelegate;

  public TestChatModel() {
    this(null);
  }

  public TestChatModel(ChatOptions defaultOptions) {
    this.defaultOptions = defaultOptions;
    this.callResponse = response();
    this.streamPublisher = Flux.just(callResponse);
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    lastSpanContext = Span.current().getSpanContext();
    if (callFailure != null) {
      throw callFailure;
    }
    if (callDelegate != null) {
      return callDelegate.call(prompt);
    }
    if (callStreamDelegate != null) {
      return requireNonNull(callStreamDelegate.stream(prompt).blockLast());
    }
    return callResponse;
  }

  @SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    if (streamFailure != null) {
      throw streamFailure;
    }
    if (streamDelegate != null) {
      return streamDelegate.stream(prompt);
    }
    if (deferredStreamDelegate != null) {
      return Flux.defer(() -> deferredStreamDelegate.stream(prompt));
    }
    return Flux.defer(
        () -> {
          lastSpanContext = Span.current().getSpanContext();
          return streamPublisher;
        });
  }

  @Override
  public ChatOptions getDefaultOptions() {
    if (defaultOptionsFailure != null) {
      throw defaultOptionsFailure;
    }
    return defaultOptions;
  }

  public SpanContext getLastSpanContext() {
    return lastSpanContext;
  }

  public void setDefaultOptionsFailure(RuntimeException defaultOptionsFailure) {
    this.defaultOptionsFailure = defaultOptionsFailure;
  }

  public void setCallFailure(RuntimeException callFailure) {
    this.callFailure = callFailure;
  }

  public void setCallResponse(ChatResponse callResponse) {
    this.callResponse = callResponse;
  }

  public void setCallDelegate(TestChatModel callDelegate) {
    this.callDelegate = callDelegate;
  }

  public void setCallStreamDelegate(TestChatModel callStreamDelegate) {
    this.callStreamDelegate = callStreamDelegate;
  }

  public void setStreamPublisher(Flux<ChatResponse> streamPublisher) {
    this.streamPublisher = streamPublisher;
  }

  public void setStreamFailure(RuntimeException streamFailure) {
    this.streamFailure = streamFailure;
  }

  public void setStreamDelegate(TestChatModel streamDelegate) {
    this.streamDelegate = streamDelegate;
  }

  public void setDeferredStreamDelegate(TestChatModel deferredStreamDelegate) {
    this.deferredStreamDelegate = deferredStreamDelegate;
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
