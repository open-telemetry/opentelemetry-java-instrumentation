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

  private final HibernateOperation hibernateOperation;
  private final Context context;
  private final Scope scope;
  private final Instrumenter<HibernateOperation, Void> instrumenter;

  private HibernateOperationScope(
      HibernateOperation hibernateOperation,
      Context context,
      Scope scope,
      Instrumenter<HibernateOperation, Void> instrumenter) {
    this.hibernateOperation = hibernateOperation;
    this.context = context;
    this.scope = scope;
    this.instrumenter = instrumenter;
  }

  /**
   * Starts operation scope
   *
   * @param hibernateOperation hibernate operation
   * @param parentContext parent context
   * @param instrumenter instrumenter
   * @return operation scope, to be ended with {@link #end(HibernateOperationScope, Throwable)} on
   *     exit advice. Might return {@literal null} when operation should not be captured.
   */
  public static HibernateOperationScope start(
      HibernateOperation hibernateOperation,
      Context parentContext,
      Instrumenter<HibernateOperation, Void> instrumenter) {

    if (!instrumenter.shouldStart(parentContext, hibernateOperation)) {
      return null;
    }

    Context context = instrumenter.start(parentContext, hibernateOperation);

    return new HibernateOperationScope(
        hibernateOperation, context, context.makeCurrent(), instrumenter);
  }

  /**
   * Performs call depth increase and returns {@literal true} when depth is > 0, which indicates a
   * nested hibernate operation is in progress. Must be called in the enter advice with an
   * unconditional corresponding call to {@link #end(HibernateOperationScope, Throwable)} to
   * decrement call depth.
   */
  public static boolean enterDepthSkipCheck() {
    CallDepth callDepth = CallDepth.forClass(HibernateOperation.class);
    return callDepth.getAndIncrement() > 0;
  }

  /**
   * Ends operation scope.
   *
   * @param scope hibernate operation scope or {@literal null} when there is none
   * @param throwable thrown exception
   */
  public static void end(HibernateOperationScope scope, Throwable throwable) {

    CallDepth callDepth = CallDepth.forClass(HibernateOperation.class);
    if (callDepth.decrementAndGet() > 0) {
      return;
    }

    if (scope != null) {
      scope.end(throwable);
    }
  }

  private void end(Throwable throwable) {
    scope.close();
    instrumenter.end(context, hibernateOperation, null, throwable);
  }
}
