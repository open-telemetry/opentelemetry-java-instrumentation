/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import static io.opentelemetry.instrumentation.servlet.v3_0.copied.Servlet3Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3RequestAdviceScope {
  private final CallDepth callDepth;
  private final ServletRequestContext<HttpServletRequest> requestContext;
  private final Context context;
  private final Scope scope;

  public Servlet3RequestAdviceScope(
      CallDepth callDepth,
      HttpServletRequest request,
      HttpServletResponse response,
      Object servletOrFilter) {
    this.callDepth = callDepth;
    this.callDepth.getAndIncrement();

    Context currentContext = Context.current();
    Context attachedContext = helper().getServerContext(request);
    Context contextToUpdate;

    requestContext = new ServletRequestContext<>(request, servletOrFilter);
    if (attachedContext == null && helper().shouldStart(currentContext, requestContext)) {
      context = helper().start(currentContext, requestContext);
      helper().setAsyncListenerResponse(context, response);

      contextToUpdate = context;
    } else if (attachedContext != null
        && helper().needsRescoping(currentContext, attachedContext)) {
      // Given request already has a context associated with it.
      // see the needsRescoping() javadoc for more explanation
      contextToUpdate = attachedContext;
      context = null;
    } else {
      // We are inside nested servlet/filter/app-server span, don't create new span
      contextToUpdate = currentContext;
      context = null;
    }

    // Update context with info from current request to ensure that server span gets the best
    // possible name.
    // In case server span was created by app server instrumentations calling updateContext
    // returns a new context that contains servlet context path that is used in other
    // instrumentations for naming server span.
    MappingResolver mappingResolver = Servlet3Singletons.getMappingResolver(servletOrFilter);
    boolean servlet = servletOrFilter instanceof Servlet;
    contextToUpdate = helper().updateContext(contextToUpdate, request, mappingResolver, servlet);
    scope = contextToUpdate.makeCurrent();

    if (context != null) {
      // Only trigger response customizer once, so only if server span was created here
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(contextToUpdate, response, Servlet3Accessor.INSTANCE);
    }
  }

  public void exit(
      @Nullable Throwable throwable, HttpServletRequest request, HttpServletResponse response) {

    boolean topLevel = callDepth.decrementAndGet() == 0;
    helper().end(requestContext, request, response, throwable, topLevel, context, scope);
  }
}
