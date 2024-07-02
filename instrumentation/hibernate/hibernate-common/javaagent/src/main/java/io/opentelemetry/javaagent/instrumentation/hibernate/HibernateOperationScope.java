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
   * Ends operation scope
   *
   * @param o {@link HibernateOperationScope} or {@link CallDepth} from enter advice
   * @param instrumenter instrumenter
   * @param throwable thrown exception
   */
  public static void end(
      Object o, Instrumenter<HibernateOperation, Void> instrumenter, Throwable throwable) {
    if (o instanceof CallDepth) {
      ((CallDepth) o).decrementAndGet();
      return;
    }

    if (!(o instanceof HibernateOperationScope)) {
      throw new IllegalArgumentException("unexpected argument");
    }

    HibernateOperationScope state = (HibernateOperationScope) o;
    int depth = state.callDepth.decrementAndGet();
    if (depth != 0) {
      throw new IllegalStateException("unexpected call depth " + depth);
    }
    state.scope.close();
    instrumenter.end(state.context, state.hibernateOperation, null, throwable);
  }
}
