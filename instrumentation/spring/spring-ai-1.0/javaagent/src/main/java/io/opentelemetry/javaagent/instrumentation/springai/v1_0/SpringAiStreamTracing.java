/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public final class SpringAiStreamTracing {
  public static Flux<ChatResponse> wrap(Flux<ChatResponse> source, SpringAiRequest request) {
    return Flux.defer(
        () -> {
          Instrumenter<SpringAiRequest, ChatResponse> instrumenter =
              SpringAiSingletons.instrumenter();
          Context parentContext = Context.current();
          if (!instrumenter.shouldStart(parentContext, request)) {
            return source;
          }
          Context context = instrumenter.start(parentContext, request);
          SpringAiMessageAttributes.setInputMessages(context, request);
          SpringAiMessageEvents.emitPromptEvents(context, request);
          AtomicBoolean ended = new AtomicBoolean();
          AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
          StringBuilder streamedContent = new StringBuilder();
          Flux<ChatResponse> traced =
              source
                  .doOnNext(
                      response -> {
                        lastResponse.set(response);
                        SpringAiMessageEvents.appendResponseContent(streamedContent, response);
                      })
                  .doOnError(
                      error ->
                          end(
                              instrumenter,
                              context,
                              request,
                              lastResponse.get(),
                              streamedContent.toString(),
                              error,
                              ended))
                  .doOnComplete(
                      () ->
                          end(
                              instrumenter,
                              context,
                              request,
                              lastResponse.get(),
                              streamedContent.toString(),
                              null,
                              ended))
                  .doOnCancel(
                      () ->
                          end(
                              instrumenter,
                              context,
                              request,
                              lastResponse.get(),
                              streamedContent.toString(),
                              null,
                              ended));
          return ContextPropagationOperator.runWithContext(traced, context);
        });
  }

  private static void end(
      Instrumenter<SpringAiRequest, ChatResponse> instrumenter,
      Context context,
      SpringAiRequest request,
      ChatResponse response,
      String streamedContent,
      Throwable error,
      AtomicBoolean ended) {
    if (ended.compareAndSet(false, true)) {
      SpringAiMessageAttributes.setOutputMessages(context, response, streamedContent);
      SpringAiMessageEvents.emitResponseEvents(context, request, response, streamedContent);
      instrumenter.end(context, request, response, error);
    }
  }

  private SpringAiStreamTracing() {}
}
