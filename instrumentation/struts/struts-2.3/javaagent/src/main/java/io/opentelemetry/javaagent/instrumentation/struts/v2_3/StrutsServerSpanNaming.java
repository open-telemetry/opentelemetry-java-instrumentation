/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v2_3;

import com.opensymphony.xwork2.ActionProxy;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;

public class StrutsServerSpanNaming {

  private static final HttpServerRouteGetter<ActionProxy> serverSpanName =
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

  public static HttpServerRouteGetter<ActionProxy> serverSpanName() {
    return serverSpanName;
  }

  private StrutsServerSpanNaming() {}
}
