/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.ServletHelper;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatHelper<REQUEST, RESPONSE> {
  protected final Instrumenter<Request, Response> instrumenter;
  protected final TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider;
  private final ServletHelper<REQUEST, RESPONSE> servletHelper;

  protected TomcatHelper(
      Instrumenter<Request, Response> instrumenter,
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider,
      ServletHelper<REQUEST, RESPONSE> servletHelper) {
    this.instrumenter = instrumenter;
    this.servletEntityProvider = servletEntityProvider;
    this.servletHelper = servletHelper;
  }

  public Context startSpan(Context parentContext, Request request) {
    Context context = instrumenter.start(parentContext, request);

    context = ServerSpanNaming.init(context, CONTAINER);
    return AppServerBridge.init(context);
  }

  public void stopSpan(
      Request request, Response response, Throwable throwable, Context context, Scope scope) {
    if (scope != null) {
      scope.close();
    }

    if (context == null) {
      return;
    }

    if (throwable == null) {
      throwable = AppServerBridge.getException(context);
    }

    if (throwable != null) {
      instrumenter.end(context, request, response, throwable);
      return;
    }

    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    if (servletRequest != null && servletHelper.mustEndOnHandlerMethodExit(servletRequest)) {
      instrumenter.end(context, request, response, null);
    }
  }

  public void attachResponseToRequest(Request request, Response response) {
    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    RESPONSE servletResponse = servletEntityProvider.getServletResponse(response);

    if (servletRequest != null && servletResponse != null) {
      servletHelper.setAsyncListenerResponse(servletRequest, servletResponse);
    }
  }
}
