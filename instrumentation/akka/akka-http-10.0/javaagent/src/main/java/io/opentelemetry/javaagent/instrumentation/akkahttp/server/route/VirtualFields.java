/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class VirtualFields {

  private VirtualFields() {}

  public static final VirtualField<PathMatcher<?>, String> PATH_MATCHER_ROUTE =
      VirtualField.find(PathMatcher.class, String.class);
}
