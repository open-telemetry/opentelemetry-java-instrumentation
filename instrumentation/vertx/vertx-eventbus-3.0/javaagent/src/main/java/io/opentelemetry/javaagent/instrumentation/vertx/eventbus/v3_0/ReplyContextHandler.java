/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.propagator;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

/**
 * Runs the handling of a reply in the context the reply carries, the one of the consumer that
 * replied. A reply that is delivered locally would otherwise get it from the thread that delivers
 * it, and a reply from another node of a cluster would not get it at all. Vert.x 3 registers reply
 * handlers as regular {@code MessageConsumer} handlers, later versions are handled by {@link
 * ReplyHandlerInstrumentation}.
 */
final class ReplyContextHandler<T> implements Handler<Message<T>> {

  private final Handler<Message<T>> delegate;

  ReplyContextHandler(Handler<Message<T>> delegate) {
    this.delegate = delegate;
  }

  static Context replyContext(Message<?> reply) {
    return propagator()
        .extract(
            Context.current(), EventBusRequest.forConsumer(reply), MessageHeaderGetter.INSTANCE);
  }

  @Override
  public void handle(Message<T> reply) {
    if (NativeTracing.isEnabledForCurrentContext()) {
      delegate.handle(reply);
      return;
    }
    try (Scope ignored = replyContext(reply).makeCurrent()) {
      delegate.handle(reply);
    }
  }
}
