/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class Servlet5Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-5.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .setMappingResolverFunction(Servlet5Singletons::getMappingResolver)
              .addContextCustomizer(
                  (context, request, attributes) ->
                      ServerSpanNaming.init(
                          context, request.servletOrFilter() instanceof Servlet ? SERVLET : FILTER))
              .build(INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE);

  private static final ServletHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new ServletHelper<>(INSTRUMENTER, Servlet5Accessor.INSTANCE);

  public static ServletHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private static MappingResolver getMappingResolver(
      ServletRequestContext<?> servletRequestContext) {
    return getMappingResolver(servletRequestContext.servletOrFilter());
  }

  public static MappingResolver getMappingResolver(Object servletOrFilter) {
    MappingResolver.Factory factory = getMappingResolverFactory(servletOrFilter);
    if (factory != null) {
      return factory.get();
    }
    return null;
  }

  private static MappingResolver.Factory getMappingResolverFactory(Object servletOrFilter) {
    boolean servlet = servletOrFilter instanceof Servlet;
    if (servlet) {
      return InstrumentationContext.get(Servlet.class, MappingResolver.Factory.class)
          .get((Servlet) servletOrFilter);
    } else {
      return InstrumentationContext.get(Filter.class, MappingResolver.Factory.class)
          .get((Filter) servletOrFilter);
    }
  }

  private Servlet5Singletons() {}
}
