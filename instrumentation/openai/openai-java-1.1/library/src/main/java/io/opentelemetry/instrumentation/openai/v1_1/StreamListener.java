/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class StreamListener {

  private final Context context;
  private final ChatCompletionCreateParams request;
  private final List<StreamedMessageBuffer> choiceBuffers;

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
    choiceBuffers = new ArrayList<>();
    hasEnded = new AtomicBoolean();
  }

  void onChunk(ChatCompletionChunk chunk) {
    model = chunk.model();
    responseId = chunk.id();
    chunk.usage().ifPresent(u -> usage = u);

    for (ChatCompletionChunk.Choice choice : chunk.choices()) {
      while (choiceBuffers.size() <= choice.index()) {
        choiceBuffers.add(null);
      }
      StreamedMessageBuffer buffer = choiceBuffers.get((int) choice.index());
      if (buffer == null) {
        buffer = new StreamedMessageBuffer(choice.index(), captureMessageContent);
        choiceBuffers.set((int) choice.index(), buffer);
      }
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
                choiceBuffers.stream()
                    .map(StreamedMessageBuffer::toChoice)
                    .collect(Collectors.toList()));

    if (usage != null) {
      result.usage(usage);
    }

    if (newSpan) {
      instrumenter.end(context, request, result.build(), error);
    }
  }
}
