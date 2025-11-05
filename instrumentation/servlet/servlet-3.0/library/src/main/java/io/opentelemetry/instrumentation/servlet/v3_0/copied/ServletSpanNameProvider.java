/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteBiGetter;
import javax.annotation.Nullable;

/** Helper class for constructing span name for given servlet/filter mapping and request. */
public class ServletSpanNameProvider<REQUEST>
    implements HttpServerRouteBiGetter<MappingResolver, REQUEST> {
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
