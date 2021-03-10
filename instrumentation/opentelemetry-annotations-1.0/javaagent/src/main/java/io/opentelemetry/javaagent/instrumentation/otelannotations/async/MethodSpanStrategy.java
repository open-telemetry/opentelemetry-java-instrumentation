/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

/**
 * Represents an implementation of a strategy for composing over the return value of a traced
 * method. If the return value represents the result of an asynchronous operation the implementation
 * can compose or register for notification of completion at which point the span representing the
 * invocation of the method will be ended.
 */
public interface MethodSpanStrategy extends ImplicitContextKeyed {
  ContextKey<MethodSpanStrategy> CONTEXT_KEY =
      ContextKey.named("opentelemetry-spring-autoconfigure-aspects-method-span-strategy");

  /**
   * Denotes the end of the invocation of the traced method with a successful result which will end
   * the span stored in the passed {@code context}. If the method returned a value representing an
   * asynchronous operation then the span will remain open until the asynchronous operation has
   * completed.
   *
   * @param tracer {@link BaseTracer} tracer to be used to end the span stored in the {@code
   *     context}.
   * @param result Return value of the traced method.
   * @return Either {@code result} or a value composing over {@code result} for notification of
   *     completion.
   */
  Object end(BaseTracer tracer, Context context, Object result);

  @Override
  default Context storeInContext(Context context) {
    return context.with(CONTEXT_KEY, this);
  }

  static MethodSpanStrategy fromContext(Context context) {
    MethodSpanStrategy methodSpanStrategy = context.get(CONTEXT_KEY);
    return methodSpanStrategy != null ? methodSpanStrategy : synchronous();
  }

  /**
   * Returns a {@link MethodSpanStrategy} for tracing synchronous methods where the return value
   * does not represent the completion of an asynchronous operation.
   */
  static MethodSpanStrategy synchronous() {
    return SynchronousMethodSpanStrategy.INSTANCE;
  }

  /**
   * Returns a {@link MethodSpanStrategy} for tracing a method that returns a {@link
   * java.util.concurrent.CompletionStage} representing the completion of an asynchronous operation.
   */
  static MethodSpanStrategy forCompletionStage() {
    return CompletionStageMethodSpanStrategy.INSTANCE;
  }

  /**
   * Returns a {@link MethodSpanStrategy} for tracing a method that returns a {@link
   * java.util.concurrent.CompletableFuture} representing the completion of an asynchronous
   * operation.
   */
  static MethodSpanStrategy forCompletableFuture() {
    return CompletableFutureMethodSpanStrategy.INSTANCE;
  }
}
