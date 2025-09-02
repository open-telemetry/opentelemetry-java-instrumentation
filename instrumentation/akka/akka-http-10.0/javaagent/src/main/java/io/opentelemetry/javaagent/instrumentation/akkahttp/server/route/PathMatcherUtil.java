/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class PathMatcherUtil {

  private static final VirtualField<PathMatcher<?>, String> PATH_MATCHER_ROUTE =
      VirtualField.find(PathMatcher.class, String.class);

  public static void setMatched(PathMatcher<?> matcher, String route) {
    PATH_MATCHER_ROUTE.set(matcher, route);
  }

  public static String getMatched(PathMatcher<?> matcher) {
    return PATH_MATCHER_ROUTE.get(matcher);
  }

  private PathMatcherUtil() {}
}
