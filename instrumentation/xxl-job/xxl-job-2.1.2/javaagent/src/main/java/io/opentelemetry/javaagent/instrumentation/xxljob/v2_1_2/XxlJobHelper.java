/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import com.xxl.job.core.biz.model.ReturnT;
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
      Object result,
      XxlJobProcessRequest request,
      Throwable throwable,
      Scope scope,
      Context context) {
    if (scope == null) {
      return;
    }
    if (result != null && (result instanceof ReturnT)) {
      ReturnT<?> res = (ReturnT<?>) result;
      if (res.getCode() == ReturnT.FAIL_CODE) {
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
