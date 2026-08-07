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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

public final class SpringAiStreamTracing {
  public static Flux<ChatResponse> wrap(Flux<ChatResponse> source, SpringAiRequest request) {
    return Flux.defer(() -> start(source, request));
  }

  private static Flux<ChatResponse> start(Flux<ChatResponse> source, SpringAiRequest request) {
    Instrumenter<SpringAiRequest, ChatResponse> instrumenter;
    Context context;
    try {
      instrumenter = SpringAiSingletons.instrumenter();
      Context parentContext = Context.current();
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
              SpringAiSingletons.captureMessageContent()
                  || SpringAiSingletons.captureMessageContentAsSpanAttributes());
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
    @Nullable private ChatResponse lastResponse;
    @Nullable private final List<StringBuilder> streamedContents;

    private StreamState(boolean captureContent) {
      streamedContents = captureContent ? new ArrayList<>() : null;
    }

    private synchronized void add(ChatResponse response) {
      lastResponse = response;
      if (streamedContents == null) {
        return;
      }
      try {
        List<Generation> generations = response.getResults();
        while (streamedContents.size() < generations.size()) {
          streamedContents.add(new StringBuilder());
        }
        for (int index = 0; index < generations.size(); index++) {
          String content = generations.get(index).getOutput().getText();
          if (content != null) {
            streamedContents.get(index).append(content);
          }
        }
      } catch (Throwable ignored) {
        // Telemetry state must not affect the instrumented publisher.
      }
    }

    private synchronized Snapshot snapshot() {
      if (streamedContents == null) {
        return new Snapshot(lastResponse, null);
      }
      List<String> contents = new ArrayList<>(streamedContents.size());
      for (StringBuilder content : streamedContents) {
        contents.add(content.toString());
      }
      return new Snapshot(lastResponse, contents);
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
