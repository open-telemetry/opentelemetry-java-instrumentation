/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;

public final class RestletServerSpanNaming {

  public static final ServerSpanNameSupplier<String> SERVER_SPAN_NAME =
      (context, pattern) -> {
        if (pattern == null || pattern.equals("")) {
          return null;
        }

        return ServletContextPath.prepend(context, pattern);
      };

  private RestletServerSpanNaming() {}
}
