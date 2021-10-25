/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.function.Function;

/**
 * The context key here is used to propagate the servlet context path throughout the request, so
 * that routing framework instrumentation that updates the span name with a more specific route can
 * prepend the servlet context path in front of that route.
 *
 * <p>This needs to be in the instrumentation-api module, instead of injected as a helper class into
 * the different modules that need it, in order to make sure that there is only a single instance of
 * the context key, since otherwise instrumentation across different class loaders would use
 * different context keys and not be able to share the servlet context path.
 */
public final class ServletContextPath {

  // Keeps track of the servlet context path that needs to be prepended to the route when updating
  // the span name
  private static final ContextKey<ServletContextPath> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-context-path-key");

  public static <REQUEST> Context init(
      Context context, Function<REQUEST, String> contextPathExtractor, REQUEST request) {
    ServletContextPath servletContextPath = context.get(CONTEXT_KEY);
    if (servletContextPath != null) {
      return context;
    }
    String contextPath = contextPathExtractor.apply(request);
    if (contextPath == null) {
      // context path isn't know yet
      return context;
    }
    if (contextPath.isEmpty() || contextPath.equals("/")) {
      // normalize empty context path to null
      contextPath = null;
    }
    return context.with(CONTEXT_KEY, new ServletContextPath(contextPath));
  }

  private final String contextPath;

  private ServletContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  /**
   * Returns a concatenation of a servlet context path stored in the given {@code context} and a
   * given {@code spanName}. If there is no servlet path stored in the context, returns {@code
   * spanName}.
   */
  public static String prepend(Context context, String spanName) {
    ServletContextPath servletContextPath = context.get(CONTEXT_KEY);
    if (servletContextPath != null) {
      String value = servletContextPath.contextPath;
      if (value != null) {
        if (spanName == null || spanName.isEmpty()) {
          return value;
        } else {
          return value + (spanName.startsWith("/") ? spanName : ("/" + spanName));
        }
      }
    }

    return spanName;
  }
}
