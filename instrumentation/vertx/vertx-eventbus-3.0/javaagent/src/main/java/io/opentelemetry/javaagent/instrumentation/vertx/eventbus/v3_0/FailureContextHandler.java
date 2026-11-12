/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import javax.annotation.Nullable;

/**
 * Handles the failure of a request nobody replied to, because there was no consumer or the reply
 * timed out, in the caller's context, like the callbacks of other client operations. A failure a
 * consumer replied with is a reply, which is handled in the context it carries.
 *
 * <p>Vert.x 3 is handed the reply handler. Later versions also take one in some methods, but turn
 * it into a {@code ReplyHandler}, which {@link ReplyHandlerInstrumentation} takes care of.
 */
final class FailureContextHandler<T> implements Handler<AsyncResult<T>> {

  private final Handler<AsyncResult<T>> delegate;
  private final Context callerContext;

  private FailureContextHandler(Handler<AsyncResult<T>> delegate, Context callerContext) {
    this.delegate = delegate;
    this.callerContext = callerContext;
  }

  /** Must be called before the producer operation starts, while the caller's context is current. */
  @Nullable
  @SuppressWarnings("unchecked")
  static Handler<?> wrap(@Nullable Handler<?> replyHandler) {
    if (replyHandler == null || replyHandler instanceof FailureContextHandler) {
      return replyHandler;
    }
    return new FailureContextHandler<>(
        (Handler<AsyncResult<Object>>) replyHandler, Context.current());
  }

  static boolean isUnanswered(@Nullable Throwable failure) {
    return !(failure instanceof ReplyException)
        || ((ReplyException) failure).failureType() != ReplyFailure.RECIPIENT_FAILURE;
  }

  @Override
  public void handle(AsyncResult<T> result) {
    if (result.failed() && isUnanswered(result.cause())) {
      try (Scope ignored = callerContext.makeCurrent()) {
        delegate.handle(result);
      }
      return;
    }
    delegate.handle(result);
  }
}
