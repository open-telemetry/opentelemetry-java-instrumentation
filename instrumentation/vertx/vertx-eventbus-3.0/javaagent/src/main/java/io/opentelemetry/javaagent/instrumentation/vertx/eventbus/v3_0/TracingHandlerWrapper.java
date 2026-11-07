/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.consumerInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

final class TracingHandlerWrapper<T> implements Handler<Message<T>> {

  private final Handler<Message<T>> delegate;

  TracingHandlerWrapper(Handler<Message<T>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void handle(Message<T> message) {
    if (NativeTracing.isEnabledForCurrentContext()) {
      delegate.handle(message);
      return;
    }

    EventBusRequest request = EventBusRequest.forConsumer(message);
    Context parentContext = Context.current();
    if (!consumerInstrumenter().shouldStart(parentContext, request)) {
      delegate.handle(message);
      return;
    }

    Context context = consumerInstrumenter().start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      delegate.handle(message);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      consumerInstrumenter().end(context, request, null, error);
    }
  }
}
