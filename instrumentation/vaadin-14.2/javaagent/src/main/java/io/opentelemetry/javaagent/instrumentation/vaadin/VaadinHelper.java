/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.REQUEST_HANDLER_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.SERVICE_CONTEXT_KEY;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;

public class VaadinHelper {
  private final Instrumenter<VaadinHandlerRequest, Void> requestHandlerInstrumenter;
  private final Instrumenter<VaadinServiceRequest, Void> serviceInstrumenter;

  public VaadinHelper(
      Instrumenter<VaadinHandlerRequest, Void> requestHandlerInstrumenter,
      Instrumenter<VaadinServiceRequest, Void> serviceInstrumenter) {
    this.requestHandlerInstrumenter = requestHandlerInstrumenter;
    this.serviceInstrumenter = serviceInstrumenter;
  }

  @Nullable
  public Context startVaadinServiceSpan(VaadinServiceRequest request) {
    Context parentContext = Context.current();
    if (!serviceInstrumenter.shouldStart(parentContext, request)) {
      return null;
    }

    return serviceInstrumenter.start(parentContext, request);
  }

  public void endVaadinServiceSpan(
      Context context, VaadinServiceRequest request, Throwable throwable) {
    serviceInstrumenter.end(context, request, null, throwable);

    ServerSpanNaming.updateServerSpanName(
        context,
        ServerSpanNaming.Source.CONTROLLER,
        (c, req) -> getSpanNameForVaadinServiceContext(c, req),
        request);
  }

  private static String getSpanNameForVaadinServiceContext(
      Context context, VaadinServiceRequest request) {
    VaadinServiceContext vaadinServiceContext = context.get(SERVICE_CONTEXT_KEY);
    // None of the request handlers processed the request, set span name to main request processing
    // method name that calls request handlers.
    if (!vaadinServiceContext.isRequestHandled()) {
      return request.getSpanName();
    }

    // Name of the request handler that handled the request.
    return vaadinServiceContext.getSpanNameCandidate();
  }

  @Nullable
  public Context startRequestHandlerSpan(VaadinHandlerRequest request) {
    Context parentContext = Context.current();
    // ignore nested request handlers
    if (parentContext.get(REQUEST_HANDLER_CONTEXT_KEY) != null) {
      return null;
    }

    VaadinServiceContext vaadinServiceContext = parentContext.get(SERVICE_CONTEXT_KEY);
    if (vaadinServiceContext != null && !vaadinServiceContext.isRequestHandled()) {
      // We don't really know whether this request handler is going to be the one that processes
      // the request, if it isn't then next handler will also update server span name candidate.
      vaadinServiceContext.setSpanNameCandidate(request.getSpanName());
    }

    if (!requestHandlerInstrumenter.shouldStart(parentContext, request)) {
      return null;
    }

    return requestHandlerInstrumenter.start(parentContext, request);
  }

  public void endRequestHandlerSpan(
      Context context, VaadinHandlerRequest request, Throwable throwable, boolean handled) {
    requestHandlerInstrumenter.end(context, request, null, throwable);

    // request handler returns true when it processes the request, if that is the case then
    // mark request as handled
    if (handled) {
      VaadinServiceContext vaadinServiceContext = context.get(SERVICE_CONTEXT_KEY);
      if (vaadinServiceContext != null) {
        vaadinServiceContext.setRequestHandled();
      }
    }
  }

  public void updateServerSpanName(UI ui) {
    if (ui != null) {
      Location location = ui.getInternals().getActiveViewLocation();
      updateServerSpanName(location);
    }
  }

  public void updateServerSpanName(Location location) {
    Context context = Context.current();
    ServerSpanNaming.updateServerSpanName(
        context,
        ServerSpanNaming.Source.NESTED_CONTROLLER,
        (c, loc) -> ServletContextPath.prepend(c, getSpanNameForLocation(loc)),
        location);
  }

  private static String getSpanNameForLocation(Location location) {
    String path = location.getPath();
    if (!path.isEmpty()) {
      path = "/" + path;
    }
    return path;
  }
}
