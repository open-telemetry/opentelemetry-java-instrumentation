/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class AkkaRouteHolderSingletons {

  public static final VirtualField<PathMatcher<?>, String> PREFIX =
      VirtualField.find(PathMatcher.class, String.class);

  private AkkaRouteHolderSingletons() {}
}
