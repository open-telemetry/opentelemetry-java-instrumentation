/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;

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

  public Context start(Context parentContext, Request request) {
    Context context = instrumenter.start(parentContext, request);
    request.setAttribute(ServletHelper.CONTEXT_ATTRIBUTE, context);
    return context;
  }

  public void end(
      Request request, Response response, Throwable throwable, Context context, Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (throwable == null) {
      throwable = AppServerBridge.getException(context);
    }

    if (throwable != null || servletHelper.mustEndOnHandlerMethodExit(context)) {
      instrumenter.end(context, request, response, throwable);
    }
  }

  public void attachResponseToRequest(Context context, Response response) {
    RESPONSE servletResponse = servletEntityProvider.getServletResponse(response);

    if (servletResponse != null) {
      servletHelper.setAsyncListenerResponse(context, servletResponse);
    }
  }

  static String messageBytesToString(MessageBytes messageBytes) {
    // on tomcat 10.1.0 MessageBytes.toString() has a side effect. Calling it caches the string
    // value and changes type of the MessageBytes from T_BYTES to T_STR which breaks request
    // processing in CoyoteAdapter.postParseRequest when it is called on MessageBytes from
    // request.requestURI().
    if (messageBytes.getType() == MessageBytes.T_BYTES) {
      return messageBytes.getByteChunk().toString();
    }
    return messageBytes.toString();
  }
}
