/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.JsonField;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

final class TracingStreamedResponse implements StreamResponse<ChatCompletionChunk> {

  private final StreamResponse<ChatCompletionChunk> delegate;
  private final Context parentCtx;
  private final ChatCompletionCreateParams request;
  private final List<StreamedMessageBuffer> choiceBuffers;

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;
  private final boolean newSpan;

  @Nullable private CompletionUsage usage;
  @Nullable private String model;
  @Nullable private String responseId;
  private boolean hasEnded = false;

  TracingStreamedResponse(
      StreamResponse<ChatCompletionChunk> delegate,
      Context parentCtx,
      ChatCompletionCreateParams request,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent,
      boolean newSpan) {
    this.delegate = delegate;
    this.parentCtx = parentCtx;
    this.request = request;
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
    this.newSpan = newSpan;
    choiceBuffers = new ArrayList<>();
  }

  @Override
  public Stream<ChatCompletionChunk> stream() {
    return StreamSupport.stream(new TracingSpliterator(delegate.stream().spliterator()), false);
  }

  @Override
  public void close() {
    endSpan();
    delegate.close();
  }

  private synchronized void endSpan() {
    if (hasEnded) {
      return;
    }
    hasEnded = true;

    ChatCompletion.Builder result =
        ChatCompletion.builder()
            .created(0)
            .choices(
                choiceBuffers.stream()
                    .map(StreamedMessageBuffer::toChoice)
                    .collect(Collectors.toList()));
    if (model != null) {
      result.model(model);
    } else {
      result.model(JsonField.ofNullable(null));
    }
    if (responseId != null) {
      result.id(responseId);
    } else {
      result.id(JsonField.ofNullable(null));
    }
    if (usage != null) {
      result.usage(usage);
    }

    if (newSpan) {
      instrumenter.end(parentCtx, request, result.build(), null);
    }
  }

  private class TracingSpliterator implements Spliterator<ChatCompletionChunk> {

    private final Spliterator<ChatCompletionChunk> delegateSpliterator;

    private TracingSpliterator(Spliterator<ChatCompletionChunk> delegateSpliterator) {
      this.delegateSpliterator = delegateSpliterator;
    }

    @Override
    public boolean tryAdvance(Consumer<? super ChatCompletionChunk> action) {
      boolean chunkReceived =
          delegateSpliterator.tryAdvance(
              chunk -> {
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
                        eventLogger,
                        choice.index(),
                        buffer.finishReason,
                        buffer.toEventBody(),
                        parentCtx);
                  }
                }

                action.accept(chunk);
              });
      if (!chunkReceived) {
        endSpan();
      }
      return chunkReceived;
    }

    @Override
    @Nullable
    public Spliterator<ChatCompletionChunk> trySplit() {
      // do not support parallelism to reliably catch the last chunk
      return null;
    }

    @Override
    public long estimateSize() {
      return delegateSpliterator.estimateSize();
    }

    @Override
    public long getExactSizeIfKnown() {
      return delegateSpliterator.getExactSizeIfKnown();
    }

    @Override
    public int characteristics() {
      return delegateSpliterator.characteristics();
    }

    @Override
    public Comparator<? super ChatCompletionChunk> getComparator() {
      return delegateSpliterator.getComparator();
    }
  }
}
