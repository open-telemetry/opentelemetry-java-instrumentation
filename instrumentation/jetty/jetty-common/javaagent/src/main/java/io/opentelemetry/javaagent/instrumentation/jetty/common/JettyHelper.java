/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.common;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;

public class JettyHelper<REQUEST, RESPONSE> extends ServletHelper<REQUEST, RESPONSE> {

  public JettyHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    super(instrumenter, accessor);
  }

  public Context start(Context parentContext, ServletRequestContext<REQUEST> requestContext) {
    return start(parentContext, requestContext, CONTAINER);
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
      // on jetty versions before 9.4 exceptions from servlet don't propagate to this method
      // check from request whether a throwable has been stored there
      throwable = errorException(request);
    }

    ServletResponseContext<RESPONSE> responseContext =
        new ServletResponseContext<>(response, throwable);
    if (throwable != null || mustEndOnHandlerMethodExit(request)) {
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }

  private Throwable errorException(REQUEST request) {
    Object value = accessor.getRequestAttribute(request, errorExceptionAttributeName());

    if (value instanceof Throwable) {
      return (Throwable) value;
    } else {
      return null;
    }
  }

  private static String errorExceptionAttributeName() {
    return "javax.servlet.error.exception";
  }
}
