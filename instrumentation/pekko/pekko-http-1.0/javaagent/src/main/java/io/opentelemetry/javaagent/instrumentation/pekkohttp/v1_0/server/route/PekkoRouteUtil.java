/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.pekko.http.scaladsl.server.PathMatcher;

public final class PekkoRouteUtil {
  public static final VirtualField<PathMatcher<?>, String> PREFIX =
      VirtualField.find(PathMatcher.class, String.class);

  private PekkoRouteUtil() {}
}
