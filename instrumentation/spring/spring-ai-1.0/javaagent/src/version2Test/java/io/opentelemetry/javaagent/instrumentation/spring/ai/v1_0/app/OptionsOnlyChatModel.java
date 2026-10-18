/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.app;

import static java.util.Collections.singletonList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class OptionsOnlyChatModel implements ChatModel {

  @Override
  public ChatResponse call(Prompt prompt) {
    String model = prompt.getOptions() != null ? prompt.getOptions().getModel() : null;
    Generation generation =
        new Generation(
            new AssistantMessage("A trace represents an end-to-end request."),
            ChatGenerationMetadata.builder().finishReason("stop").build());
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .id("response-id")
            .model(model != null ? model : "test-model")
            .usage(new DefaultUsage(3, 2))
            .build();
    return new ChatResponse(singletonList(generation), metadata);
  }

  @SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.just(call(prompt));
  }
}
