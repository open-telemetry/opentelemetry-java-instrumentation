/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.common.v8_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.common.ServletHelper;
import javax.annotation.Nullable;

public class JettyHelper<REQUEST, RESPONSE> extends ServletHelper<REQUEST, RESPONSE> {

  public JettyHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    super(instrumenter, accessor);
  }

  public void end(
      ServletRequestContext<REQUEST> requestContext,
      REQUEST request,
      RESPONSE response,
      @Nullable Throwable throwable,
      Context context,
      Scope scope) {

    scope.close();

    if (throwable == null) {
      // on jetty versions before 9.4 exceptions from servlet don't propagate to this method
      // check from request whether a throwable has been stored there
      throwable = errorException(request);
    }

    if (throwable != null || mustEndOnHandlerMethodExit(context)) {
      ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }

  @Nullable
  private Throwable errorException(REQUEST request) {
    Object value = accessor.getRequestAttribute(request, errorExceptionAttributeName());

    if (value instanceof Throwable) {
      return (Throwable) value;
    } else {
      return null;
    }
  }

  private static String errorExceptionAttributeName() {
    // this method is only used on jetty versions before 9.4
    return "javax.servlet.error.exception";
  }
}
