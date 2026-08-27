/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.stream.Collectors.toList;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

final class StreamListener {

  private final Context context;
  private final ChatCompletionCreateParams request;
  private final Map<Long, StreamedMessageBuffer> choiceBuffers;

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;
  private final boolean newSpan;
  private final AtomicBoolean hasEnded;

  @Nullable private CompletionUsage usage;
  @Nullable private String model;
  @Nullable private String responseId;

  StreamListener(
      Context context,
      ChatCompletionCreateParams request,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent,
      boolean newSpan) {
    this.context = context;
    this.request = request;
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
    this.newSpan = newSpan;
    choiceBuffers = new TreeMap<>();
    hasEnded = new AtomicBoolean();
  }

  void onChunk(ChatCompletionChunk chunk) {
    model = chunk.model();
    responseId = chunk.id();
    chunk.usage().ifPresent(u -> usage = u);

    for (ChatCompletionChunk.Choice choice : chunk.choices()) {
      StreamedMessageBuffer buffer =
          choiceBuffers.computeIfAbsent(
              choice.index(), index -> new StreamedMessageBuffer(index, captureMessageContent));
      buffer.append(choice.delta());
      if (choice.finishReason().isPresent()) {
        buffer.finishReason = choice.finishReason().get().toString();

        // message has ended, let's emit
        ChatCompletionEventsHelper.emitCompletionLogEvent(
            context, eventLogger, choice.index(), buffer.finishReason, buffer.toEventBody());
      }
    }
  }

  void endSpan(@Nullable Throwable error) {
    // Use an atomic operation since close() type of methods are exposed to the user
    // and can come from any thread.
    if (!hasEnded.compareAndSet(false, true)) {
      return;
    }

    if (model == null || responseId == null) {
      // Only happens if we got no chunks, so we have no response.
      if (newSpan) {
        instrumenter.end(context, request, null, error);
      }
      return;
    }

    ChatCompletion.Builder result =
        ChatCompletion.builder()
            .created(0)
            .model(model)
            .id(responseId)
            .choices(
                choiceBuffers.values().stream()
                    .map(StreamedMessageBuffer::toChoice)
                    .collect(toList()));

    if (usage != null) {
      result.usage(usage);
    }

    if (newSpan) {
      instrumenter.end(context, request, result.build(), error);
    }
  }
}
