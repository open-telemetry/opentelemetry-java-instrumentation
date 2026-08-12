/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

public class SpringAiStreamTracing {
  public static Flux<ChatResponse> wrap(Flux<ChatResponse> source, SpringAiRequest request) {
    return Flux.deferContextual(reactorContext -> start(source, request, reactorContext));
  }

  private static Flux<ChatResponse> start(
      Flux<ChatResponse> source, SpringAiRequest request, ContextView reactorContext) {
    Instrumenter<SpringAiRequest, ChatResponse> instrumenter;
    Context context;
    try {
      instrumenter = SpringAiSingletons.instrumenter();
      Context parentContext =
          ContextPropagationOperator.getOpenTelemetryContextFromContextView(
              reactorContext, Context.current());
      if (!instrumenter.shouldStart(parentContext, request)) {
        return source;
      }
      context = instrumenter.start(parentContext, request);
    } catch (Throwable ignored) {
      // This method runs outside of Byte Buddy advice when the publisher is subscribed.
      return source;
    }

    try {
      SpringAiMessageAttributes.setInputMessages(context, request);
      SpringAiMessageEvents.emitPromptEvents(context, request);
      AtomicBoolean ended = new AtomicBoolean();
      StreamState state =
          new StreamState(
              SpringAiSingletons.captureMessageContent(),
              SpringAiSingletons.captureMessageContentAsSpanAttributes(),
              SpringAiSingletons.messageContentSpanAttributeMaxLength());
      Flux<ChatResponse> traced =
          source
              .doOnNext(state::add)
              .doOnError(error -> end(instrumenter, context, request, state, error, ended))
              .doOnComplete(() -> end(instrumenter, context, request, state, null, ended))
              .doOnCancel(() -> end(instrumenter, context, request, state, null, ended));
      return ContextPropagationOperator.runWithContext(traced, context);
    } catch (Throwable ignored) {
      // Do not leak an already-started span if Reactor rejects operator assembly.
      endStartedSpan(instrumenter, context, request);
      return source;
    }
  }

  private static void endStartedSpan(
      Instrumenter<SpringAiRequest, ChatResponse> instrumenter,
      Context context,
      SpringAiRequest request) {
    try {
      instrumenter.end(context, request, null, null);
    } catch (Throwable ignored) {
      // This callback is outside of Byte Buddy advice suppression.
    }
  }

  private static void end(
      Instrumenter<SpringAiRequest, ChatResponse> instrumenter,
      Context context,
      SpringAiRequest request,
      StreamState state,
      @Nullable Throwable error,
      AtomicBoolean ended) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }

    @Nullable ChatResponse response = null;
    @Nullable List<String> streamedContents = null;
    try {
      Snapshot snapshot = state.snapshot();
      response = snapshot.response;
      streamedContents = snapshot.streamedContents;
    } catch (Throwable ignored) {
      // Telemetry state must not affect the instrumented publisher.
    }

    SpringAiMessageAttributes.setOutputMessages(context, response, streamedContents);
    SpringAiMessageEvents.emitResponseEvents(context, request, response, streamedContents);
    try {
      instrumenter.end(context, request, response, error);
    } catch (Throwable ignored) {
      // This callback is outside of Byte Buddy advice suppression.
    }
  }

  private static final class StreamState {
    private boolean hasResponse;
    private final List<GenerationState> generations = new ArrayList<>();
    @Nullable private String responseId;
    @Nullable private String responseModel;
    @Nullable private Usage usage;
    @Nullable private final List<ContentBuffer> streamedContents;
    private final int contentMaxLength;

    private StreamState(
        boolean captureMessageContent,
        boolean captureMessageContentAsSpanAttributes,
        int spanAttributeMaxLength) {
      if (!captureMessageContent && !captureMessageContentAsSpanAttributes) {
        streamedContents = null;
        contentMaxLength = 0;
      } else {
        streamedContents = new ArrayList<>();
        contentMaxLength = captureMessageContent ? -1 : spanAttributeMaxLength;
      }
    }

    private synchronized void add(ChatResponse response) {
      hasResponse = true;
      try {
        List<Generation> generations = response.getResults();
        while (this.generations.size() < generations.size()) {
          this.generations.add(new GenerationState());
          if (streamedContents != null) {
            streamedContents.add(new ContentBuffer(contentMaxLength));
          }
        }
        for (int index = 0; index < generations.size(); index++) {
          Generation generation = generations.get(index);
          this.generations.get(index).add(generation);
          if (streamedContents != null) {
            String content = generation.getOutput().getText();
            if (content != null) {
              streamedContents.get(index).append(content);
            }
          }
        }

        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata != null) {
          if (metadata.getId() != null && !metadata.getId().isEmpty()) {
            responseId = metadata.getId();
          }
          if (metadata.getModel() != null && !metadata.getModel().isEmpty()) {
            responseModel = metadata.getModel();
          }
          Usage newUsage = metadata.getUsage();
          if (newUsage != null && !(newUsage instanceof EmptyUsage)) {
            usage = newUsage;
          }
        }
      } catch (Throwable ignored) {
        // Telemetry state must not affect the instrumented publisher.
      }
    }

    private synchronized Snapshot snapshot() {
      if (!hasResponse) {
        return new Snapshot(null, null);
      }

      List<Generation> responseGenerations = new ArrayList<>(generations.size());
      for (GenerationState generation : generations) {
        Generation value = generation.value();
        if (value != null) {
          responseGenerations.add(value);
        }
      }

      ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
      if (responseId != null) {
        metadata.id(responseId);
      }
      if (responseModel != null) {
        metadata.model(responseModel);
      }
      if (usage != null) {
        metadata.usage(usage);
      }
      ChatResponse response = new ChatResponse(responseGenerations, metadata.build());

      if (streamedContents == null) {
        return new Snapshot(response, null);
      }
      List<String> contents = new ArrayList<>(streamedContents.size());
      for (ContentBuffer content : streamedContents) {
        contents.add(content.value());
      }
      return new Snapshot(response, contents);
    }
  }

  private static final class GenerationState {
    @Nullable private Generation generation;
    @Nullable private String finishReason;

    private void add(Generation generation) {
      this.generation = generation;
      ChatGenerationMetadata metadata = generation.getMetadata();
      if (metadata != null && metadata.getFinishReason() != null) {
        finishReason = metadata.getFinishReason();
      }
    }

    @Nullable
    private Generation value() {
      if (generation == null || finishReason == null) {
        return generation;
      }
      ChatGenerationMetadata metadata = generation.getMetadata();
      if (metadata != null && finishReason.equals(metadata.getFinishReason())) {
        return generation;
      }
      return new Generation(
          generation.getOutput(),
          ChatGenerationMetadata.builder().finishReason(finishReason).build());
    }
  }

  private static final class ContentBuffer {
    private final StringBuilder content = new StringBuilder();
    private final int maxLength;
    private boolean truncated;

    private ContentBuffer(int maxLength) {
      this.maxLength = maxLength;
    }

    private void append(String value) {
      if (maxLength < 0) {
        content.append(value);
        return;
      }
      if (truncated) {
        return;
      }

      int remaining = maxLength - content.length();
      if (remaining <= 0) {
        truncated = true;
        return;
      }
      int end = Math.min(value.length(), remaining);
      if (end < value.length()
          && end > 0
          && Character.isHighSurrogate(value.charAt(end - 1))
          && Character.isLowSurrogate(value.charAt(end))) {
        end--;
      }
      content.append(value, 0, end);
      truncated = end < value.length();
    }

    private String value() {
      int length = content.length();
      if (maxLength >= 0
          && length == maxLength
          && length > 0
          && Character.isHighSurrogate(content.charAt(length - 1))) {
        return content.substring(0, length - 1);
      }
      return content.toString();
    }
  }

  private static final class Snapshot {
    @Nullable private final ChatResponse response;
    @Nullable private final List<String> streamedContents;

    private Snapshot(@Nullable ChatResponse response, @Nullable List<String> streamedContents) {
      this.response = response;
      this.streamedContents = streamedContents;
    }
  }

  private SpringAiStreamTracing() {}
}
