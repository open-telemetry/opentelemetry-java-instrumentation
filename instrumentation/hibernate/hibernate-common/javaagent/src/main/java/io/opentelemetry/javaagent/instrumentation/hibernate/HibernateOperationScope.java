/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;

public class HibernateOperationScope {

  private final CallDepth callDepth;
  private final HibernateOperation hibernateOperation;
  private final Context context;
  private final Scope scope;

  private HibernateOperationScope(
      CallDepth callDepth, HibernateOperation hibernateOperation, Context context, Scope scope) {
    this.callDepth = callDepth;
    this.hibernateOperation = hibernateOperation;
    this.context = context;
    this.scope = scope;
  }

  /**
   * Starts operation scope
   *
   * @param callDepth call depth
   * @param hibernateOperation hibernate operation
   * @param parentContext parent context
   * @param instrumenter instrumenter
   * @return operation scope, to be ended with {@link #end(HibernateOperationScope, Instrumenter,
   *     Throwable)} on exit advice
   */
  public static HibernateOperationScope startNew(
      CallDepth callDepth,
      HibernateOperation hibernateOperation,
      Context parentContext,
      Instrumenter<HibernateOperation, Void> instrumenter) {

    Context context = instrumenter.start(parentContext, hibernateOperation);
    return new HibernateOperationScope(
        callDepth, hibernateOperation, context, context.makeCurrent());
  }

  /**
   * Builds hibernate operation scope from an existing call depth for cases where only the call
   * depth is needed
   *
   * @param callDepth call depth
   * @return hibernate operation scope wrapping the provided call depth.
   */
  public static HibernateOperationScope wrapCallDepth(CallDepth callDepth) {
    return new HibernateOperationScope(callDepth, null, null, null);
  }

  /**
   * Ends operation scope
   *
   * @param hibernateOperationScope hibernate operation scope
   * @param instrumenter instrumenter
   * @param throwable thrown exception
   */
  public static void end(
      HibernateOperationScope hibernateOperationScope,
      Instrumenter<HibernateOperation, Void> instrumenter,
      Throwable throwable) {

    if (hibernateOperationScope.context == null) {
      // call depth only
      hibernateOperationScope.callDepth.decrementAndGet();
      return;
    }

    int depth = hibernateOperationScope.callDepth.decrementAndGet();
    if (depth != 0) {
      throw new IllegalStateException("unexpected call depth " + depth);
    }
    hibernateOperationScope.scope.close();
    instrumenter.end(
        hibernateOperationScope.context,
        hibernateOperationScope.hibernateOperation,
        null,
        throwable);
  }
}
