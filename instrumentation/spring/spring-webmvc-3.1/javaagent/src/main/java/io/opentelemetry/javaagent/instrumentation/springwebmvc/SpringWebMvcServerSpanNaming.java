/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

public class SpringWebMvcServerSpanNaming {

  public static Supplier<String> getServerSpanNameSupplier(
      Context context, HttpServletRequest request) {
    return () -> getServerSpanName(context, request);
  }

  public static String getServerSpanName(Context context, HttpServletRequest request) {
    Object bestMatchingPattern =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (bestMatchingPattern != null) {
      return ServletContextPath.prepend(context, bestMatchingPattern.toString());
    }
    return null;
  }
}
