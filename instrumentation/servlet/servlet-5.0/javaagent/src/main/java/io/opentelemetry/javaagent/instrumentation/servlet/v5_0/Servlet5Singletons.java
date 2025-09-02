/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.ResponseInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.servlet.snippet.OutputStreamSnippetInjectionHelper;
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
              .build(INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE);

  private static final ServletHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new ServletHelper<>(INSTRUMENTER, Servlet5Accessor.INSTANCE);

  public static final VirtualField<Servlet, MappingResolver.Factory> SERVLET_MAPPING_RESOLVER =
      VirtualField.find(Servlet.class, MappingResolver.Factory.class);
  public static final VirtualField<Filter, MappingResolver.Factory> FILTER_MAPPING_RESOLVER =
      VirtualField.find(Filter.class, MappingResolver.Factory.class);

  private static final Instrumenter<ClassAndMethod, Void> RESPONSE_INSTRUMENTER =
      ResponseInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);
  private static final OutputStreamSnippetInjectionHelper SNIPPET_INJECTION_HELPER =
      new OutputStreamSnippetInjectionHelper(() -> ExperimentalSnippetHolder.getSnippet());

  public static ServletHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  public static Instrumenter<ClassAndMethod, Void> responseInstrumenter() {
    return RESPONSE_INSTRUMENTER;
  }

  public static OutputStreamSnippetInjectionHelper getSnippetInjectionHelper() {
    return SNIPPET_INJECTION_HELPER;
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
      return SERVLET_MAPPING_RESOLVER.get((Servlet) servletOrFilter);
    } else {
      return FILTER_MAPPING_RESOLVER.get((Filter) servletOrFilter);
    }
  }

  private Servlet5Singletons() {}
}
