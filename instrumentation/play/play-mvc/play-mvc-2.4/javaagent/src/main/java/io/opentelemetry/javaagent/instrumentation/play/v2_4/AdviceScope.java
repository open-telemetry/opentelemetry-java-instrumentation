/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.updateSpan;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

/** Container used to carry state between enter and exit advices */
public final class AdviceScope {

  private final ActionData actionData;
  private final Context context;
  private final Scope scope;

  public AdviceScope(Context context, Scope scope, ActionData actionData) {
    this.actionData = actionData;
    this.context = context;
    this.scope = scope;
  }

  @Nullable
  public static AdviceScope start(Context parentContext, ActionData actionData) {
    if (!instrumenter().shouldStart(parentContext, actionData)) {
      return null;
    }

    Context context = instrumenter().start(parentContext, actionData);
    return new AdviceScope(context, context.makeCurrent(), actionData);
  }

  public void closeScope() {
    if (scope != null) {
      scope.close();
    }
  }

  public void end(
      Throwable throwable, Future<Result> responseFuture, Action<?> thisAction, Request<?> req) {
    closeScope();
    updateSpan(context, req);

    if (throwable == null) {
      // span finished in RequestCompleteCallback
      responseFuture.onComplete(
          new RequestCompleteCallback(context), thisAction.executionContext());
    } else {
      instrumenter().end(context, actionData, null, throwable);
    }
  }
}
