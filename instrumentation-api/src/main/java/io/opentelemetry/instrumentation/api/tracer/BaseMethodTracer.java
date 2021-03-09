/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.async.MethodSpanStrategies;
import io.opentelemetry.instrumentation.api.tracer.async.MethodSpanStrategy;
import java.lang.reflect.Method;

/**
 * Base class for instrumentation specific tracer implementations that trace the invocation of a
 * method.
 */
public abstract class BaseMethodTracer extends BaseTracer {

  public BaseMethodTracer() {}

  public BaseMethodTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  /**
   * Resolves the {@link MethodSpanStrategy} for tracing the specified {@code method} and stores
   * that strategy in the returned {@code Context}.
   */
  protected Context withMethodSpanStrategy(Context context, Method method) {
    MethodSpanStrategy methodSpanStrategy = MethodSpanStrategies.resolveStrategy(method);
    return methodSpanStrategy.storeInContext(context);
  }

  /**
   * Denotes the end of the invocation of the traced method with a successful result which will end
   * the span stored in the passed {@code context}. If the method returned a value representing an
   * asynchronous operation then the span will remain open until the asynchronous operation has
   * completed.
   *
   * @param result Return value from the traced method.
   * @return Either {@code result} or a value composing over {@code result} for notification of
   *     completion.
   */
  public Object end(Context context, Object result) {
    return MethodSpanStrategy.fromContext(context).end(this, context, result);
  }
}
