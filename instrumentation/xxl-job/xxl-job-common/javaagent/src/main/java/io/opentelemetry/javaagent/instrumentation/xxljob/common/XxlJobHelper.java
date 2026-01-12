/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public final class XxlJobHelper {
  private final Instrumenter<XxlJobProcessRequest, Void> instrumenter;
  private final Predicate<Object> failedStatusPredicate;

  public static class XxlJobScope {
    private final XxlJobProcessRequest request;
    private final Context context;
    private final Scope scope;

    private XxlJobScope(XxlJobProcessRequest request, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.scope = scope;
    }
  }

  private XxlJobHelper(
      Instrumenter<XxlJobProcessRequest, Void> instrumenter,
      Predicate<Object> failedStatusPredicate) {
    this.instrumenter = instrumenter;
    this.failedStatusPredicate = failedStatusPredicate;
  }

  public static XxlJobHelper create(
      Instrumenter<XxlJobProcessRequest, Void> instrumenter,
      Predicate<Object> failedStatusPredicate) {
    return new XxlJobHelper(instrumenter, failedStatusPredicate);
  }

  @Nullable
  public XxlJobScope startSpan(XxlJobProcessRequest request) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, request);
    return new XxlJobScope(request, context, context.makeCurrent());
  }

  public void endSpan(
      @Nullable XxlJobScope scope, @Nullable Object result, @Nullable Throwable throwable) {
    if (scope == null) {
      return;
    }
    if (failedStatusPredicate.test(result)) {
      scope.request.setFailed();
    }
    scope.scope.close();
    instrumenter.end(scope.context, scope.request, null, throwable);
  }
}
