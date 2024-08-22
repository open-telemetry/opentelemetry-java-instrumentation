/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Predicate;
import tech.powerjob.worker.core.processor.ProcessResult;

public final class PowerJobHelper {

  private final Instrumenter<PowerJobProcessRequest, Void> instrumenter;

  private final Predicate<ProcessResult> failedStatusPredicate;

  private PowerJobHelper(
      Instrumenter<PowerJobProcessRequest, Void> instrumenter,
      Predicate<ProcessResult> failedStatusPredicate) {
    this.instrumenter = instrumenter;
    this.failedStatusPredicate = failedStatusPredicate;
  }

  public static PowerJobHelper create(
      Instrumenter<PowerJobProcessRequest, Void> instrumenter,
      Predicate<ProcessResult> failedStatusPredicate) {
    return new PowerJobHelper(instrumenter, failedStatusPredicate);
  }

  public Context startSpan(Context parentContext, PowerJobProcessRequest request) {
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    return instrumenter.start(parentContext, request);
  }

  public void stopSpan(
      ProcessResult result,
      PowerJobProcessRequest request,
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
}
