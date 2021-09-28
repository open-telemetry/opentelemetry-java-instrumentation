/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public abstract class ServletHttpServerTracer<REQUEST, RESPONSE>
    extends HttpServerTracer<REQUEST, RESPONSE, REQUEST, REQUEST> {

  private static final Logger logger = LoggerFactory.getLogger(ServletHttpServerTracer.class);

  public static final String ASYNC_LISTENER_ATTRIBUTE =
      ServletHttpServerTracer.class.getName() + ".AsyncListener";
  public static final String ASYNC_LISTENER_RESPONSE_ATTRIBUTE =
      ServletHttpServerTracer.class.getName() + ".AsyncListenerResponse";

  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);

  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final Function<REQUEST, String> contextPathExtractor;

  protected ServletHttpServerTracer(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
    this.contextPathExtractor = accessor::getRequestContextPath;
  }

  public Context startSpan(REQUEST request, String spanName, boolean servlet) {
    Context context = startSpan(request, request, request, spanName);

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    // we do this e.g. so that servlet containers can use these values in their access logs
    accessor.setRequestAttribute(request, "trace_id", spanContext.getTraceId());
    accessor.setRequestAttribute(request, "span_id", spanContext.getSpanId());

    // server span name shouldn't be updated when server span was created from a call to Servlet
    // (if created from a call to Filter then name may be updated from updateContext)
    ServerSpanNaming.updateSource(context, servlet ? SERVLET : FILTER);

    return addServletContextPath(context, request);
  }

  @Override
  protected Context customizeContext(Context context, REQUEST request) {
    // add context for tracking whether servlet instrumentation has updated the server span name
    context = ServerSpanNaming.init(context, CONTAINER);
    // add context for current request's context path
    return addServletContextPath(context, request);
  }

  protected Context addServletContextPath(Context context, REQUEST request) {
    return ServletContextPath.init(context, contextPathExtractor, request);
  }

  @Override
  public void endExceptionally(
      Context context, Throwable throwable, RESPONSE response, long timestamp) {
    if (accessor.isResponseCommitted(response)) {
      super.endExceptionally(context, throwable, response, timestamp);
    } else {
      // passing null response to super, in order to capture as 500 / INTERNAL, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      super.endExceptionally(context, throwable, null, timestamp);
    }
  }

  @Override
  protected String scheme(REQUEST httpServletRequest) {
    return accessor.getRequestScheme(httpServletRequest);
  }

  @Override
  protected String host(REQUEST httpServletRequest) {
    return accessor.getRequestServerName(httpServletRequest)
        + ":"
        + accessor.getRequestServerPort(httpServletRequest);
  }

  @Override
  protected String target(REQUEST httpServletRequest) {
    String target = accessor.getRequestUri(httpServletRequest);
    String queryString = accessor.getRequestQueryString(httpServletRequest);
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  public Context getServerContext(REQUEST request) {
    Object context = accessor.getRequestAttribute(request, CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected void attachServerContext(Context context, REQUEST request) {
    accessor.setRequestAttribute(request, CONTEXT_ATTRIBUTE, context);
  }

  @Override
  protected Integer peerPort(REQUEST connection) {
    return accessor.getRequestRemotePort(connection);
  }

  @Override
  protected String peerHostIp(REQUEST connection) {
    return accessor.getRequestRemoteAddr(connection);
  }

  @Override
  protected String method(REQUEST request) {
    return accessor.getRequestMethod(request);
  }

  @Override
  protected int responseStatus(RESPONSE response) {
    return accessor.getResponseStatus(response);
  }

  @Override
  protected abstract TextMapGetter<REQUEST> getGetter();

  /**
   * Response object must be attached to a request prior to {@link #attachAsyncListener(Object)}
   * being called, as otherwise in some environments it is not possible to access response from
   * async event in listeners.
   */
  public void setAsyncListenerResponse(REQUEST request, RESPONSE response) {
    accessor.setRequestAttribute(request, ASYNC_LISTENER_RESPONSE_ATTRIBUTE, response);
  }

  public void attachAsyncListener(REQUEST request) {
    Context context = getServerContext(request);

    if (context != null) {
      Object response = accessor.getRequestAttribute(request, ASYNC_LISTENER_RESPONSE_ATTRIBUTE);

      accessor.addRequestAsyncListener(
          request, new TagSettingAsyncListener<>(this, new AtomicBoolean(), context), response);
      accessor.setRequestAttribute(request, ASYNC_LISTENER_ATTRIBUTE, true);
    }
  }

  public boolean isAsyncListenerAttached(REQUEST request) {
    return accessor.getRequestAttribute(request, ASYNC_LISTENER_ATTRIBUTE) != null;
  }

  public void addUnwrappedThrowable(Context context, Throwable throwable) {
    if (AppServerBridge.shouldRecordException(context)) {
      onException(context, throwable);
    }
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (accessor.isServletException(throwable) && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  public void setPrincipal(Context context, REQUEST request) {
    Principal principal = accessor.getRequestUserPrincipal(request);
    if (principal != null) {
      Span.fromContext(context).setAttribute(SemanticAttributes.ENDUSER_ID, principal.getName());
    }
  }

  @Override
  protected String flavor(REQUEST connection, REQUEST request) {
    return accessor.getRequestProtocol(connection);
  }

  @Override
  protected String requestHeader(REQUEST httpServletRequest, String name) {
    return accessor.getRequestHeader(httpServletRequest, name);
  }

  public Throwable errorException(REQUEST request) {
    Object value = accessor.getRequestAttribute(request, errorExceptionAttributeName());

    if (value instanceof Throwable) {
      return (Throwable) value;
    } else {
      return null;
    }
  }

  protected abstract String errorExceptionAttributeName();

  public String getSpanName(REQUEST request) {
    String servletPath = accessor.getRequestServletPath(request);
    if (servletPath.isEmpty()) {
      return "HTTP " + accessor.getRequestMethod(request);
    }
    String contextPath = accessor.getRequestContextPath(request);
    if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
      return servletPath;
    }
    return contextPath + servletPath;
  }

  public void onTimeout(Context context, long timeout) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("servlet.timeout", timeout);
    }
    span.end();
  }

  /*
  Given request already has a context associated with it.
  As there should not be nested spans of kind SERVER, we should NOT create a new span here.

  But it may happen that there is no span in current Context or it is from a different trace.
  E.g. in case of async servlet request processing we create span for incoming request in one thread,
  but actual request continues processing happens in another thread.
  Depending on servlet container implementation, this processing may again arrive into this method.
  E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

  In this case we have to put the span from the request into current context before continuing.
  */
  public boolean needsRescoping(Context currentContext, Context attachedContext) {
    return !sameTrace(Span.fromContext(currentContext), Span.fromContext(attachedContext));
  }

  private static boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getSpanContext().getTraceId().equals(otherSpan.getSpanContext().getTraceId());
  }
}
