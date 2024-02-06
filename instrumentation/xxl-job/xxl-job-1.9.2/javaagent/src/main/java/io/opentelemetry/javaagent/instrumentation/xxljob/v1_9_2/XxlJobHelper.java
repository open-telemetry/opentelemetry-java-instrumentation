/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import com.xxl.job.core.biz.model.ReturnT;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;

public class XxlJobHelper {

  private XxlJobHelper() {}

  public static Context startSpan(Context parentContext, XxlJobProcessRequest request) {
    if (!XxlJobSingletons.xxlJobProcessInstrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return XxlJobSingletons.xxlJobProcessInstrumenter().start(parentContext, request);
  }

  public static void stopSpan(
      Object result,
      XxlJobProcessRequest request,
      Throwable throwable,
      Scope scope,
      Context context) {
    if (result != null && (result instanceof ReturnT)) {
      ReturnT<?> res = (ReturnT<?>) result;
      if (res.getCode() == ReturnT.FAIL_CODE) {
        request.setResultStatus(Boolean.FALSE.toString());
      }
    }
    if (throwable != null) {
      request.setResultStatus(Boolean.FALSE.toString());
    }
    if (scope != null) {
      scope.close();
      XxlJobSingletons.xxlJobProcessInstrumenter().end(context, request, null, throwable);
    }
  }
}
