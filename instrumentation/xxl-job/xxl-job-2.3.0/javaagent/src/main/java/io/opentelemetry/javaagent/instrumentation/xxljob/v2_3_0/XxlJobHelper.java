/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static com.xxl.job.core.context.XxlJobContext.HANDLE_COCE_SUCCESS;
import static io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0.XxlJobSingletons.instrumenter;

import com.xxl.job.core.context.XxlJobContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;

public class XxlJobHelper {

  private XxlJobHelper() {}

  public static Context startSpan(Context parentContext, XxlJobProcessRequest request) {
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return instrumenter().start(parentContext, request);
  }

  public static void stopSpan(
      XxlJobProcessRequest request, Throwable throwable, Scope scope, Context context) {
    if (scope == null) {
      return;
    }
    // From 2.3.0, XxlJobContext is used to store the result of the job execution.
    XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
    if (xxlJobContext != null) {
      int handleCode = xxlJobContext.getHandleCode();
      if (handleCode != HANDLE_COCE_SUCCESS) {
        request.setSchedulingSuccess(Boolean.FALSE);
      }
    }
    if (throwable != null) {
      request.setSchedulingSuccess(Boolean.FALSE);
    }
    scope.close();
    instrumenter().end(context, request, null, throwable);
  }
}
