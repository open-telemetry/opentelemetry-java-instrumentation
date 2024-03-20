/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Predicate;

public final class XxlJobHelper {
  private final Instrumenter<XxlJobProcessRequest, Void> instrumenter;
  private final Predicate<Object> failedStatusPredicate;

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

  public Context startSpan(Context parentContext, XxlJobProcessRequest request) {
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    return instrumenter.start(parentContext, request);
  }

  public void stopSpan(
      Object result,
      XxlJobProcessRequest request,
      Throwable throwable,
      Scope scope,
      Context context) {
    if (scope == null) {
      return;
    }
    if (failedStatusPredicate.test(result)) {
      request.setFailed();
    }
    scope.close();
    instrumenter.end(context, request, null, throwable);
  }

  public void stopSpan(
      XxlJobProcessRequest request, Throwable throwable, Scope scope, Context context) {
    stopSpan(null, request, throwable, scope, context);
  }
}
