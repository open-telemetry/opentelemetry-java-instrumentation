/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;

public class LibertyHelper<REQUEST, RESPONSE> extends ServletHelper<REQUEST, RESPONSE> {

  public LibertyHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    super(instrumenter, accessor);
  }

  public void end(
      ServletRequestContext<REQUEST> requestContext,
      REQUEST request,
      RESPONSE response,
      Throwable throwable,
      Context context,
      Scope scope) {

    if (scope == null) {
      return;
    }
    scope.close();

    if (throwable == null) {
      throwable = AppServerBridge.getException(context);
    }

    ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
    if (throwable != null || mustEndOnHandlerMethodExit(context)) {
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }
}
