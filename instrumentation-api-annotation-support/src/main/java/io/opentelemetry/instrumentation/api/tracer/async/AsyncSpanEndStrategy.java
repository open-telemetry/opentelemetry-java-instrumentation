/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

/**
 * Represents an implementation of a strategy for composing over the return value of an asynchronous
 * traced method which can compose or register for notification of completion at which point the
 * span representing the invocation of the method will be ended.
 */
public interface AsyncSpanEndStrategy {
  boolean supports(Class<?> returnType);

  /**
   * Denotes the end of the invocation of the traced method with a successful result which will end
   * the span stored in the passed {@code context}. The span will remain open until the asynchronous
   * operation has completed.
   *
   * @param tracer {@link BaseTracer} tracer to be used to end the span stored in the {@code
   *     context}.
   * @param returnValue Return value from the traced method. Must be an instance of a {@code
   *     returnType} for which {@link #supports(Class)} returned true (in particular it must not be
   *     {@code null}).
   * @return Either {@code returnValue} or a value composing over {@code returnValue} for
   *     notification of completion.
   */
  Object end(BaseTracer tracer, Context context, Object returnValue);
}
