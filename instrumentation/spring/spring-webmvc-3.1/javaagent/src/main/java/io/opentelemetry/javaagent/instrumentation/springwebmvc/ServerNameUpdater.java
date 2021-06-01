/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

public class ServerNameUpdater {

  public static void updateServerSpanName(Context context, HttpServletRequest request) {
    if (request != null) {
      Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (bestMatchingPattern != null) {
        ServerSpanNaming.updateServerSpanName(
            context,
            CONTROLLER,
            () -> ServletContextPath.prepend(context, bestMatchingPattern.toString()));
      }
    }
  }
}
