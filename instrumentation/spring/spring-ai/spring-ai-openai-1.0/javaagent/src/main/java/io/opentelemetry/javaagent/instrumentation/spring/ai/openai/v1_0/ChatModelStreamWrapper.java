/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.javaagent.bootstrap.reactor.ReactorSubscribeOnProcessTracing;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import reactor.core.publisher.Flux;

public final class ChatModelStreamWrapper {

  public static Flux<ChatCompletionChunk> wrap(
      Flux<ChatCompletionChunk> originFlux,
      ChatModelStreamListener streamListener,
      Context context) {

    Flux<ChatCompletionChunk> chatCompletionChunkFlux =
        originFlux
            .doOnNext(chunk -> streamListener.onChunk(chunk))
            .doOnComplete(() -> streamListener.endSpan(null))
            .doOnError(streamListener::endSpan);
    return ContextPropagationOperator.runWithContext(chatCompletionChunkFlux, context);
  }

  public static Flux<ChatResponse> enableContextPropagation(Flux<ChatResponse> originFlux) {
    return originFlux.contextWrite(
        ctx -> ctx.put(ReactorSubscribeOnProcessTracing.CONTEXT_PROPAGATION_KEY, true));
  }

  private ChatModelStreamWrapper() {}
}
