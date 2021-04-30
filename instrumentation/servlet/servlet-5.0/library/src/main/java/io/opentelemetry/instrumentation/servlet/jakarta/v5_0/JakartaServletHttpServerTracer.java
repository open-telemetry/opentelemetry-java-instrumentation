/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.naming.ServletSpanNameProvider;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JakartaServletHttpServerTracer
    extends ServletHttpServerTracer<HttpServletRequest, HttpServletResponse> {
  private static final JakartaServletHttpServerTracer TRACER = new JakartaServletHttpServerTracer();
  private static final ServletSpanNameProvider<HttpServletRequest> SPAN_NAME_PROVIDER =
      new ServletSpanNameProvider<>(JakartaServletAccessor.INSTANCE);

  public JakartaServletHttpServerTracer() {
    super(JakartaServletAccessor.INSTANCE);
  }

  public static JakartaServletHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(
      HttpServletRequest request, MappingResolver mappingResolver, boolean servlet) {
    return startSpan(request, SPAN_NAME_PROVIDER.getSpanName(mappingResolver, request), servlet);
  }

  public Context updateContext(
      Context context,
      HttpServletRequest request,
      MappingResolver mappingResolver,
      boolean servlet) {
    ServerSpanNaming.updateServerSpanName(
        context,
        servlet ? SERVLET : FILTER,
        () -> SPAN_NAME_PROVIDER.getSpanNameOrNull(mappingResolver, request));
    return updateContext(context, request);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-5.0";
  }

  @Override
  protected TextMapGetter<HttpServletRequest> getGetter() {
    return JakartaHttpServletRequestGetter.GETTER;
  }

  @Override
  protected String errorExceptionAttributeName() {
    return RequestDispatcher.ERROR_EXCEPTION;
  }
}
