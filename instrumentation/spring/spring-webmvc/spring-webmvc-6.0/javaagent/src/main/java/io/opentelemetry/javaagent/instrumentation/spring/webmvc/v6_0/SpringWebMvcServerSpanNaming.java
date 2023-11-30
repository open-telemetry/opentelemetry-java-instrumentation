/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

public class SpringWebMvcServerSpanNaming {

  public static final HttpServerRouteGetter<HttpServletRequest> SERVER_SPAN_NAME =
      (context, request) -> {
        Object bestMatchingPattern =
            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern != null) {
          return ServletContextPath.prepend(context, bestMatchingPattern.toString());
        }
        return null;
      };

  private SpringWebMvcServerSpanNaming() {}
}
