/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import java.util.Set;

public class Servlet2SpanNameExtractor<REQUEST, RESPONSE>
    implements SpanNameExtractor<ServletRequestContext<REQUEST>> {

  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final Set<String> knownMethods = AgentCommonConfig.get().getKnownHttpRequestMethods();

  public Servlet2SpanNameExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  public String extract(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    String method = accessor.getRequestMethod(request);
    String servletPath = accessor.getRequestServletPath(request);
    if (method == null) {
      return "HTTP";
    }
    if (!knownMethods.contains(method)) {
      method = "HTTP";
    }
    if (servletPath == null || servletPath.isEmpty()) {
      return method;
    }
    String contextPath = accessor.getRequestContextPath(request);
    if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
      return method + " " + servletPath;
    }
    return method + " " + contextPath + servletPath;
  }
}
