/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v7_0;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.apache.struts2.ActionProxy;

public class StrutsServerSpanNaming {

  public static final HttpServerRouteGetter<ActionProxy> SERVER_SPAN_NAME =
      (context, actionProxy) -> {
        // We take name from the config, because it contains the path pattern from the
        // configuration.
        String result = actionProxy.getConfig().getName();

        String actionNamespace = actionProxy.getNamespace();
        if (actionNamespace != null && !actionNamespace.isEmpty()) {
          if (actionNamespace.endsWith("/") || result.startsWith("/")) {
            result = actionNamespace + result;
          } else {
            result = actionNamespace + "/" + result;
          }
        }

        if (!result.startsWith("/")) {
          result = "/" + result;
        }

        return ServletContextPath.prepend(context, result);
      };

  private StrutsServerSpanNaming() {}
}
