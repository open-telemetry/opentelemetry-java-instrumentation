/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

public final class ChatClientStreamWrapper {

  public static Flux<ChatClientResponse> wrap(
      Flux<ChatClientResponse> originFlux,
      ChatClientStreamListener streamListener,
      Context context) {

    Flux<ChatClientResponse> chatClientResponseFlux =
        originFlux
            .doOnNext(chunk -> streamListener.onChunk(chunk))
            .doOnComplete(() -> streamListener.endSpan(null))
            .doOnError(streamListener::endSpan);
    return ContextPropagationOperator.runWithContext(chatClientResponseFlux, context);
  }

  private ChatClientStreamWrapper() {}
}
