/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter2;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;

/** Helper class for constructing span name for given servlet/filter mapping and request. */
public class ServletSpanNameProvider<REQUEST>
    implements HttpRouteGetter2<MappingResolver, REQUEST> {
  private final ServletAccessor<REQUEST, ?> servletAccessor;

  public ServletSpanNameProvider(ServletAccessor<REQUEST, ?> servletAccessor) {
    this.servletAccessor = servletAccessor;
  }

  @Override
  @Nullable
  public String get(Context context, MappingResolver mappingResolver, REQUEST request) {
    String servletPath = servletAccessor.getRequestServletPath(request);
    String pathInfo = servletAccessor.getRequestPathInfo(request);
    String mapping = mappingResolver.resolve(servletPath, pathInfo);
    // mapping was not found
    if (mapping == null) {
      return null;
    }

    return ServletContextPath.prepend(context, mapping);
  }
}
