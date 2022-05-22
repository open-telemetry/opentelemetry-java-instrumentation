/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

/** Represents the source that provided the {@code http.route} attribute. */
public enum HttpRouteSource {
  // for servlet filters we try to find the best name which isn't necessarily from the first
  // filter that is called
  FILTER(1, /* useFirst= */ false),
  SERVLET(2),
  CONTROLLER(3),
  // Some frameworks, e.g. JaxRS, allow for nested controller/paths and we want to select the
  // longest one
  NESTED_CONTROLLER(4, false);

  final int order;
  final boolean useFirst;

  HttpRouteSource(int order) {
    this(order, /* useFirst= */ true);
  }

  HttpRouteSource(int order, boolean useFirst) {
    this.order = order;
    this.useFirst = useFirst;
  }
}
