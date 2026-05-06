/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public final class ChatClientStreamListener {

  private final Context context;
  private final ChatClientRequest request;
  private final Instrumenter<ChatClientRequest, ChatClientResponse> instrumenter;
  private final MessageCaptureOptions messageCaptureOptions;
  private final boolean newSpan;
  private final AtomicBoolean hasEnded;
  private final List<ChatClientMessageBuffer> chatClientMessageBuffers;

  // Aggregated metadata
  private final AtomicLong inputTokens = new AtomicLong(0);
  private final AtomicLong outputTokens = new AtomicLong(0);
  private final AtomicReference<String> requestId = new AtomicReference<>();
  private final AtomicReference<String> model = new AtomicReference<>();

  public ChatClientStreamListener(
      Context context,
      ChatClientRequest request,
      Instrumenter<ChatClientRequest, ChatClientResponse> instrumenter,
      MessageCaptureOptions messageCaptureOptions,
      boolean newSpan) {
    this.context = context;
    this.request = request;
    this.instrumenter = instrumenter;
    this.messageCaptureOptions = messageCaptureOptions;
    this.newSpan = newSpan;
    this.hasEnded = new AtomicBoolean();
    this.chatClientMessageBuffers = new ArrayList<>();
  }

  public void onChunk(ChatClientResponse chatClientChunk) {
    if (chatClientChunk == null || chatClientChunk.chatResponse() == null) {
      return;
    }

    ChatResponse chunk = chatClientChunk.chatResponse();
    if (chunk.getMetadata() != null) {
      if (chunk.getMetadata().getId() != null) {
        requestId.set(chunk.getMetadata().getId());
      }
      if (chunk.getMetadata().getUsage() != null) {
        if (chunk.getMetadata().getUsage().getPromptTokens() != null) {
          inputTokens.set(chunk.getMetadata().getUsage().getPromptTokens().longValue());
        }
        if (chunk.getMetadata().getUsage().getCompletionTokens() != null) {
          outputTokens.set(chunk.getMetadata().getUsage().getCompletionTokens().longValue());
        }
      }
    }

    if (chunk.getResults() != null) {
      List<Generation> generations = chunk.getResults();
      for (int i = 0; i < generations.size(); i++) {
        while (chatClientMessageBuffers.size() <= i) {
          chatClientMessageBuffers.add(null);
        }
        ChatClientMessageBuffer buffer = chatClientMessageBuffers.get(i);
        if (buffer == null) {
          buffer = new ChatClientMessageBuffer(i, messageCaptureOptions);
          chatClientMessageBuffers.set(i, buffer);
        }

        buffer.append(generations.get(i));
      }
    }
  }

  public void endSpan(@Nullable Throwable error) {
    // Use an atomic operation since close() type of methods are exposed to the user
    // and can come from any thread.
    if (!this.hasEnded.compareAndSet(false, true)) {
      return;
    }

    if (this.chatClientMessageBuffers.isEmpty()) {
      // Only happens if we got no chunks, so we have no response.
      if (this.newSpan) {
        this.instrumenter.end(this.context, this.request, null, error);
      }
      return;
    }

    Integer inputTokens = null;
    if (this.inputTokens.get() > 0) {
      inputTokens = (int) this.inputTokens.get();
    }

    Integer outputTokens = null;
    if (this.outputTokens.get() > 0) {
      outputTokens = (int) this.outputTokens.get();
    }

    List<Generation> generations =
        this.chatClientMessageBuffers.stream()
            .map(ChatClientMessageBuffer::toGeneration)
            .collect(Collectors.toList());

    ChatClientResponse response =
        ChatClientResponse.builder()
            .chatResponse(
                ChatResponse.builder()
                    .generations(generations)
                    .metadata(
                        ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(inputTokens, outputTokens))
                            .id(requestId.get())
                            .model(model.get())
                            .build())
                    .build())
            .build();

    if (this.newSpan) {
      this.instrumenter.end(this.context, this.request, response, error);
    }
  }
}
