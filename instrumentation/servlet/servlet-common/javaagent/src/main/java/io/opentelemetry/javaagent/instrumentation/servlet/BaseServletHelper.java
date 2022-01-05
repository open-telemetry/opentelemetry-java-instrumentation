/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import java.util.function.Function;

public abstract class BaseServletHelper<REQUEST, RESPONSE> {
  protected final Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
      instrumenter;
  protected final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final ServletSpanNameProvider<REQUEST> spanNameProvider;
  private final Function<REQUEST, String> contextPathExtractor;
  private final ServletRequestParametersExtractor<REQUEST, RESPONSE> parameterExtractor;

  protected BaseServletHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.instrumenter = instrumenter;
    this.accessor = accessor;
    this.spanNameProvider = new ServletSpanNameProvider<>(accessor);
    this.contextPathExtractor = accessor::getRequestContextPath;
    this.parameterExtractor =
        ServletRequestParametersExtractor.enabled()
            ? new ServletRequestParametersExtractor<>(accessor)
            : null;
  }

  public boolean shouldStart(Context parentContext, ServletRequestContext<REQUEST> requestContext) {
    return instrumenter.shouldStart(parentContext, requestContext);
  }

  public Context start(Context parentContext, ServletRequestContext<REQUEST> requestContext) {
    Context context = instrumenter.start(parentContext, requestContext);

    REQUEST request = requestContext.request();
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    // we do this e.g. so that servlet containers can use these values in their access logs
    // TODO: These are only available when using servlet instrumentation or when server
    //  instrumentation extends servlet instrumentation e.g. jetty. Either remove or make sure they
    //  also work on tomcat and wildfly.
    accessor.setRequestAttribute(request, "trace_id", spanContext.getTraceId());
    accessor.setRequestAttribute(request, "span_id", spanContext.getSpanId());

    context = addServletContextPath(context, request);

    attachServerContext(context, request);

    return context;
  }

  protected Context addServletContextPath(Context context, REQUEST request) {
    return ServletContextPath.init(context, contextPathExtractor, request);
  }

  public Context getServerContext(REQUEST request) {
    Object context = accessor.getRequestAttribute(request, ServletHelper.CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  private void attachServerContext(Context context, REQUEST request) {
    accessor.setRequestAttribute(request, ServletHelper.CONTEXT_ATTRIBUTE, context);
  }

  public void recordException(Context context, Throwable throwable) {
    AppServerBridge.recordException(context, throwable);
  }

  public Context updateContext(
      Context context, REQUEST request, MappingResolver mappingResolver, boolean servlet) {
    Context result = addServletContextPath(context, request);
    if (mappingResolver != null) {
      ServerSpanNaming.updateServerSpanName(
          result, servlet ? SERVLET : FILTER, spanNameProvider, mappingResolver, request);
    }

    return result;
  }

  public void captureServletAttributes(Context context, REQUEST request) {
    if (parameterExtractor == null || !AppServerBridge.captureServletAttributes(context)) {
      return;
    }
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    parameterExtractor.setAttributes(request, (key, value) -> serverSpan.setAttribute(key, value));
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
