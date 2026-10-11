/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.stream.Collectors.toList;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

final class StreamListener {

  private final Context context;
  private final ChatCompletionRequest request;

  private final Instrumenter<ChatCompletionRequest, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;
  private final boolean newSpan;

  // close() type of methods are exposed to the user and can be called from any thread, so they
  // can overlap with chunk processing on the stream handler thread
  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Map<Long, StreamedMessageBuffer> choiceBuffers = new TreeMap<>();

  @GuardedBy("lock")
  private boolean hasEnded;

  @GuardedBy("lock")
  @Nullable
  private CompletionUsage usage;

  @GuardedBy("lock")
  @Nullable
  private String model;

  @GuardedBy("lock")
  @Nullable
  private String responseId;

  StreamListener(
      Context context,
      ChatCompletionRequest request,
      Instrumenter<ChatCompletionRequest, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent,
      boolean newSpan) {
    this.context = context;
    this.request = request;
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
    this.newSpan = newSpan;
  }

  void onChunk(ChatCompletionChunk chunk) {
    List<CompletedChoice> completedChoices = new ArrayList<>();
    synchronized (lock) {
      if (hasEnded) {
        // the response has already been recorded
        return;
      }

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
          completedChoices.add(
              new CompletedChoice(choice.index(), buffer.finishReason, buffer.toEventBody()));
        }
      }
    }

    // message has ended, let's emit
    for (CompletedChoice completedChoice : completedChoices) {
      ChatCompletionEventsHelper.emitCompletionLogEvent(
          context,
          eventLogger,
          completedChoice.index,
          completedChoice.finishReason,
          completedChoice.eventBody);
    }
  }

  void endSpan(@Nullable Throwable error) {
    String model;
    String responseId;
    CompletionUsage usage;
    List<ChatCompletion.Choice> choices;
    synchronized (lock) {
      if (hasEnded) {
        return;
      }
      hasEnded = true;

      model = this.model;
      responseId = this.responseId;
      usage = this.usage;
      choices =
          choiceBuffers.values().stream().map(StreamedMessageBuffer::toChoice).collect(toList());
    }

    if (!newSpan) {
      return;
    }

    if (model == null || responseId == null) {
      // Only happens if we got no chunks, so we have no response.
      instrumenter.end(context, request, null, error);
      return;
    }

    ChatCompletion.Builder result =
        ChatCompletion.builder().created(0).model(model).id(responseId).choices(choices);

    if (usage != null) {
      result.usage(usage);
    }

    instrumenter.end(context, request, result.build(), error);
  }

  private static final class CompletedChoice {
    private final long index;
    private final String finishReason;
    private final Value<?> eventBody;

    private CompletedChoice(long index, String finishReason, Value<?> eventBody) {
      this.index = index;
      this.finishReason = finishReason;
      this.eventBody = eventBody;
    }
  }
}
