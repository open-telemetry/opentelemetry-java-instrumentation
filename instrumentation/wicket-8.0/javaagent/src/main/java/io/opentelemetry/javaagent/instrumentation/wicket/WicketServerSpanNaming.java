/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.apache.wicket.core.request.handler.IPageClassRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;

public final class WicketServerSpanNaming {

  public static final HttpRouteGetter<IPageClassRequestHandler> SERVER_SPAN_NAME =
      (context, handler) -> {
        // using class name as page name
        String pageName = handler.getPageClass().getName();
        // wicket filter mapping without wildcard, if wicket filter is mapped to /*
        // this will be an empty string
        String filterPath = RequestCycle.get().getRequest().getFilterPath();
        return ServletContextPath.prepend(context, filterPath + "/" + pageName);
      };

  private WicketServerSpanNaming() {}
}
