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
   * @return operation scope, to be ended with {@link #end(Object, Instrumenter, Throwable)} on exit
   *     advice
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
   * Ends operation scope. First parameter is passed as an object in order to minimize allocation by
   * avoinding to wrap call depth.
   *
   * @param hibernateOperationScopeOrCallDepth hibernate operation scope or call depth
   * @param instrumenter instrumenter
   * @param throwable thrown exception
   */
  public static void end(
      Object hibernateOperationScopeOrCallDepth,
      Instrumenter<HibernateOperation, Void> instrumenter,
      Throwable throwable) {

    if (hibernateOperationScopeOrCallDepth instanceof CallDepth) {
      CallDepth callDepth = (CallDepth) hibernateOperationScopeOrCallDepth;
      callDepth.decrementAndGet();
      return;
    }

    if (!(hibernateOperationScopeOrCallDepth instanceof HibernateOperationScope)) {
      throw new IllegalArgumentException(
          "unexpected hibernate operation scope or call depth: "
              + hibernateOperationScopeOrCallDepth.getClass());
    }
    HibernateOperationScope hibernateOperationScope =
        (HibernateOperationScope) hibernateOperationScopeOrCallDepth;

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
