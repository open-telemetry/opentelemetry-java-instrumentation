/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry.v5_4;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;

public class TapestryServerSpanNaming {

  private static final HttpServerRouteGetter<String> serverSpanName =
      (context, pageName) -> {
        if (pageName == null) {
          return null;
        }
        if (!pageName.isEmpty()) {
          pageName = "/" + pageName;
        }

        return ServletContextPath.prepend(context, pageName);
      };

  public static HttpServerRouteGetter<String> serverSpanName() {
    return serverSpanName;
  }

  private TapestryServerSpanNaming() {}
}
