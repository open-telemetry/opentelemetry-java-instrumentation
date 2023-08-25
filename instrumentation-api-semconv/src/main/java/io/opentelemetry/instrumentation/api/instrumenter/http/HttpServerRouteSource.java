/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

/** Represents the source that provided the {@code http.route} attribute. */
public enum HttpServerRouteSource {
  /**
   * Represents a "filter" that may execute before the actual server handler. E.g. the Servlet API
   * {@code Filter} interface. Since multiple filters may match the same request, the one with the
   * longest (most detailed) route will be chosen.
   */
  SERVER_FILTER(1, /* useFirst= */ false),
  /** Represents the actual server handler. E.g. the Servlet API {@code Servlet} interface. */
  SERVER(2),
  /**
   * Represents the controller, usually defined as part of some MVC framework. E.g. a Spring Web MVC
   * controller method, or a JAX-RS annotated resource method.
   */
  CONTROLLER(3),
  /**
   * Represents a nested controller, usually defined as part of some MVC framework. E.g. a JAX-RS
   * annotated sub-resource method. Since multiple nested controllers may match the same request,
   * the one with the longest (most detailed) route will be chosen.
   */
  NESTED_CONTROLLER(4, false);

  final int order;
  final boolean useFirst;

  HttpServerRouteSource(int order) {
    this(order, /* useFirst= */ true);
  }

  HttpServerRouteSource(int order, boolean useFirst) {
    this.order = order;
    this.useFirst = useFirst;
  }
}
