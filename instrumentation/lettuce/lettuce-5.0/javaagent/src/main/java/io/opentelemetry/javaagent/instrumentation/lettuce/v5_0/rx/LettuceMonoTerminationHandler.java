/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public class LettuceMonoTerminationHandler<T> implements LettuceReactiveCommandHandler {

  private static final Logger logger =
      Logger.getLogger(LettuceMonoTerminationHandler.class.getName());

  private final StatefulConnection<?, ?> connection;
  private final AtomicBoolean spanEnded = new AtomicBoolean();
  @Nullable private RedisCommand<?, ?, ?> command;
  @Nullable private Context context;
  private boolean expectsResponse;

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
    this.connection = connection;
  }

  @Override
  public void onCommand(RedisCommand<?, ?, ?> subscriptionCommand) {
    command = subscriptionCommand;
    expectsResponse = expectsResponse(subscriptionCommand);
    LettuceSingletons.initializeCommandPeerForSubscription(subscriptionCommand);
    LettuceSingletons.attachConnectionState(subscriptionCommand, connection);
    context = instrumenter().start(Context.current(), subscriptionCommand);
    if (!expectsResponse) {
      instrumenter().end(context, subscriptionCommand, null, null);
    }
  }

  @Override
  public void onCancel() {
    endSpan(null);
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
    return publisher.doOnSuccess(value -> endSpan(null)).doOnError(this::endSpan);
  }

  private void endSpan(@Nullable Throwable throwable) {
    if (!expectsResponse || !spanEnded.compareAndSet(false, true)) {
      return;
    }
    if (context != null && command != null) {
      instrumenter().end(context, command, null, throwable);
    } else {
      logger.fine("Failed to finish this.span because it probably wasn't started.");
    }
  }
}
