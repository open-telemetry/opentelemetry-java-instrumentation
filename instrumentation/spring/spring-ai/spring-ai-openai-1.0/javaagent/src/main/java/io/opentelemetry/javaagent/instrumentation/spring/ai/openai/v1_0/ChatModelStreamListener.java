/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.Usage;
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

public final class ChatModelStreamListener {

  private final Context context;
  private final ChatCompletionRequest request;
  private final Instrumenter<ChatCompletionRequest, ChatCompletion> instrumenter;
  private final MessageCaptureOptions messageCaptureOptions;
  private final boolean newSpan;
  private final AtomicBoolean hasEnded;
  private final List<ChatModelMessageBuffer> chatModelMessageBuffers;

  // Aggregated metadata
  private final AtomicLong inputTokens = new AtomicLong(0);
  private final AtomicLong outputTokens = new AtomicLong(0);
  private final AtomicReference<String> requestId = new AtomicReference<>();

  public ChatModelStreamListener(
      Context context,
      ChatCompletionRequest request,
      Instrumenter<ChatCompletionRequest, ChatCompletion> instrumenter,
      MessageCaptureOptions messageCaptureOptions,
      boolean newSpan) {
    this.context = context;
    this.request = request;
    this.instrumenter = instrumenter;
    this.messageCaptureOptions = messageCaptureOptions;
    this.newSpan = newSpan;
    this.hasEnded = new AtomicBoolean();
    this.chatModelMessageBuffers = new ArrayList<>();
  }

  public void onChunk(ChatCompletionChunk chunk) {
    if (chunk == null) {
      return;
    }

    if (chunk.id() != null) {
      requestId.set(chunk.id());
    }
    if (chunk.usage() != null) {
      if (chunk.usage().promptTokens() != null) {
        inputTokens.set(chunk.usage().promptTokens().longValue());
      }
      if (chunk.usage().completionTokens() != null) {
        outputTokens.set(chunk.usage().completionTokens().longValue());
      }
    }

    if (chunk.choices() != null) {
      List<ChunkChoice> choices = chunk.choices();
      for (ChunkChoice choice : choices) {
        while (chatModelMessageBuffers.size() <= choice.index()) {
          chatModelMessageBuffers.add(null);
        }
        ChatModelMessageBuffer buffer = chatModelMessageBuffers.get(choice.index());
        if (buffer == null) {
          buffer = new ChatModelMessageBuffer(choice.index(), messageCaptureOptions);
          chatModelMessageBuffers.set(choice.index(), buffer);
        }

        // Convert ChunkChoice to Choice for compatibility with buffer
        buffer.append(
            new org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice(
                choice.finishReason(),
                choice.index(),
                choice.delta(),
                choice.logprobs())
        );
      }
    }
  }

  public void endSpan(@Nullable Throwable error) {
    // Use an atomic operation since close() type of methods are exposed to the user
    // and can come from any thread.
    if (!this.hasEnded.compareAndSet(false, true)) {
      return;
    }

    if (this.chatModelMessageBuffers.isEmpty()) {
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

    List<org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice> choices = this.chatModelMessageBuffers.stream()
        .map(ChatModelMessageBuffer::toChoice)
        .collect(Collectors.toList());

    ChatCompletion result = new ChatCompletion(
        this.requestId.get(),
        choices,
        null, // created
        null, // model
        null, // serviceTier
        null, // systemFingerprint
        "chat.completion",
        new Usage(outputTokens, inputTokens, 
                  inputTokens != null && outputTokens != null ? inputTokens + outputTokens : null,
                  null, null));

    if (this.newSpan) {
      this.instrumenter.end(this.context, this.request, result, error);
    }
  }
}
