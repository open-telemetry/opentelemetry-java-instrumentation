/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import io.opentelemetry.instrumentation.api.server.ServerSpanNameSupplier;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;

public class TapestryServerSpanNaming {

  public static final ServerSpanNameSupplier<String> SERVER_SPAN_NAME =
      (context, pageName) -> {
        if (pageName == null) {
          return null;
        }
        if (!pageName.isEmpty()) {
          pageName = "/" + pageName;
        }

        return ServletContextPath.prepend(context, pageName);
      };

  private TapestryServerSpanNaming() {}
}
