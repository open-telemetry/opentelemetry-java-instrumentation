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
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatHelper<REQUEST, RESPONSE> {
  protected final Instrumenter<Request, Response> instrumenter;
  protected final TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider;
  private final ServletHelper<REQUEST, RESPONSE> servletHelper;

  public TomcatHelper(
      Instrumenter<Request, Response> instrumenter,
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider,
      ServletHelper<REQUEST, RESPONSE> servletHelper) {
    this.instrumenter = instrumenter;
    this.servletEntityProvider = servletEntityProvider;
    this.servletHelper = servletHelper;
  }

  public boolean shouldStart(Context parentContext, Request request) {
    return instrumenter.shouldStart(parentContext, request);
  }

  public Context startSpan(Context parentContext, Request request) {
    Context context = instrumenter.start(parentContext, request);

    context = ServerSpanNaming.init(context, CONTAINER);
    return AppServerBridge.init(context);
  }

  public void stopSpan(
      Request request, Response response, Throwable throwable, Context context, Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (throwable == null) {
      throwable = AppServerBridge.getException(context);
    }

    if (throwable != null || mustEndOnHandlerMethodExit(request)) {
      instrumenter.end(context, request, response, throwable);
    }
  }

  private boolean mustEndOnHandlerMethodExit(Request request) {
    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    return servletRequest != null && servletHelper.mustEndOnHandlerMethodExit(servletRequest);
  }

  public void attachResponseToRequest(Request request, Response response) {
    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    RESPONSE servletResponse = servletEntityProvider.getServletResponse(response);

    if (servletRequest != null && servletResponse != null) {
      servletHelper.setAsyncListenerResponse(servletRequest, servletResponse);
    }
  }
}
