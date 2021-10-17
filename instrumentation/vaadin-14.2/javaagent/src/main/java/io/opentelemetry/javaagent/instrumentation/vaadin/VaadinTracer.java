/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import elemental.json.JsonObject;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

public class VaadinTracer extends BaseTracer {
  private static final ContextKey<VaadinServiceContext> SERVICE_CONTEXT_KEY =
      ContextKey.named("opentelemetry-vaadin-service-context-key");
  private static final ContextKey<Object> REQUEST_HANDLER_CONTEXT_KEY =
      ContextKey.named("opentelemetry-vaadin-request-handler-context-key");

  private static final VaadinTracer TRACER = new VaadinTracer();

  public static VaadinTracer tracer() {
    return TRACER;
  }

  private VaadinTracer() {
    super(GlobalOpenTelemetry.get());
  }

  public Context startVaadinServiceSpan(VaadinService vaadinService, Method method) {
    String spanName = SpanNames.fromMethod(vaadinService.getClass(), method);
    Context context = super.startSpan(spanName);
    return context.with(SERVICE_CONTEXT_KEY, new VaadinServiceContext(spanName));
  }

  public void endSpan(Context context, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(context, throwable);
    } else {
      end(context);
    }
  }

  public void endVaadinServiceSpan(Context context, Throwable throwable) {
    endSpan(context, throwable);

    VaadinServiceContext vaadinServiceContext = context.get(SERVICE_CONTEXT_KEY);
    if (!vaadinServiceContext.isRequestHandled()) {
      // none of the request handlers processed the request
      // as we update server span name on call to each request handler currently server span name
      // is set based on the last request handler even when it didn't process the request, set
      // server span name to main request processing method name
      Span span = ServerSpan.fromContextOrNull(context);
      if (span != null) {
        span.updateName(vaadinServiceContext.vaadinServiceSpanName);
      }
    }
  }

  @Nullable
  public Context startRequestHandlerSpan(RequestHandler requestHandler, Method method) {
    Context current = Context.current();
    // ignore nested request handlers
    if (current.get(REQUEST_HANDLER_CONTEXT_KEY) != null) {
      return null;
    }

    String spanName = SpanNames.fromMethod(requestHandler.getClass(), method);
    VaadinServiceContext vaadinServiceContext = current.get(SERVICE_CONTEXT_KEY);
    if (vaadinServiceContext != null && !vaadinServiceContext.isRequestHandled()) {
      Span span = ServerSpan.fromContextOrNull(current);
      if (span != null) {
        // set server span name to request handler name
        // we don't really know whether this request handler is going to be the one
        // that process the request, if it isn't then next handler will also update
        // server span name
        span.updateName(spanName);
      }
    }

    Context context = super.startSpan(spanName);
    return context.with(REQUEST_HANDLER_CONTEXT_KEY, Boolean.TRUE);
  }

  public void endRequestHandlerSpan(Context context, Throwable throwable, boolean handled) {
    endSpan(context, throwable);

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
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null) {
      String path = location.getPath();
      if (!path.isEmpty()) {
        path = "/" + path;
      }
      span.updateName(ServletContextPath.prepend(context, path));
    }
  }

  public Context startClientCallableSpan(Class<?> componentClass, String methodName) {
    return super.startSpan(SpanNames.fromMethod(componentClass, methodName));
  }

  public Context startRpcInvocationHandlerSpan(
      RpcInvocationHandler rpcInvocationHandler, Method method, JsonObject jsonObject) {
    String spanName = SpanNames.fromMethod(rpcInvocationHandler.getClass(), method);
    if ("event".equals(rpcInvocationHandler.getRpcType())) {
      String eventType = jsonObject.getString("event");
      if (eventType != null) {
        // append event type to make span name more descriptive
        spanName += "/" + eventType;
      }
    }
    return super.startSpan(spanName);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.vaadin-14.2";
  }

  private static class VaadinServiceContext {
    final String vaadinServiceSpanName;
    boolean requestHandled;

    VaadinServiceContext(String vaadinServiceSpanName) {
      this.vaadinServiceSpanName = vaadinServiceSpanName;
    }

    void setRequestHandled() {
      requestHandled = true;
    }

    boolean isRequestHandled() {
      return requestHandled;
    }
  }
}
