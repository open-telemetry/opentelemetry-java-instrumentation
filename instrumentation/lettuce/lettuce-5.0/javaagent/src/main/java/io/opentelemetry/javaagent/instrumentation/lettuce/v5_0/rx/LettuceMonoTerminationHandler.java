/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import io.lettuce.core.api.StatefulConnection;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public class LettuceMonoTerminationHandler<T> extends LettuceReactiveCommandHandler {

  public static <T> Mono<T> monitor(Mono<T> publisher, StatefulConnection<?, ?> connection) {
    return new Mono<T>() {
      @Override
      public void subscribe(CoreSubscriber<? super T> actual) {
        LettuceMonoTerminationHandler<T> handler = new LettuceMonoTerminationHandler<>(connection);
        handler
            .finishSpanOnTerminal(publisher)
            .subscribe(new LettuceReactiveCommandSubscriber<>(actual, handler));
      }
    };
  }

  private LettuceMonoTerminationHandler(StatefulConnection<?, ?> connection) {
    super(connection);
  }

  /**
   * Registers terminal callbacks that finish the span on completion or error. {@code
   * doOnSuccessOrError} was removed in reactor 3.5, so {@code doOnSuccess} + {@code doOnError}
   * (both available across the whole supported reactor range) are used instead. The wiring lives
   * here, on an injected helper class, rather than inline in the advice, so the lambdas do not
   * become private synthetic methods on the advice class (which the instrumented class cannot
   * access).
   */
  private Mono<T> finishSpanOnTerminal(Mono<T> publisher) {
    return publisher
        .doOnSuccess(value -> finishSpan(/* isCommandCancelled= */ false, null))
        .doOnError(error -> finishSpan(/* isCommandCancelled= */ false, error));
  }
}
