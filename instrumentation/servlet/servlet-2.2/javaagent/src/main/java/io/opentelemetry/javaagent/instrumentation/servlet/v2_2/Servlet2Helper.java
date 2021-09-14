/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.servlet.BaseServletHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2Helper extends BaseServletHelper<HttpServletRequest, HttpServletResponse> {

  Servlet2Helper(
      Instrumenter<
              ServletRequestContext<HttpServletRequest>,
              ServletResponseContext<HttpServletResponse>>
          instrumenter) {
    super(instrumenter, Servlet2Accessor.INSTANCE);
  }

  public Context startSpan(
      Context parentContext, ServletRequestContext<HttpServletRequest> requestContext) {
    return start(parentContext, requestContext, SERVLET);
  }

  public void stopSpan(
      Context context,
      ServletRequestContext<HttpServletRequest> requestContext,
      HttpServletResponse response,
      int statusCode,
      Throwable throwable) {

    ServletResponseContext<HttpServletResponse> responseContext =
        new ServletResponseContext<>(response, throwable);
    responseContext.setStatus(statusCode);

    instrumenter.end(context, requestContext, responseContext, throwable);
  }

  public Context updateContext(Context context, HttpServletRequest request) {
    return addServletContextPath(context, request);
  }
}
