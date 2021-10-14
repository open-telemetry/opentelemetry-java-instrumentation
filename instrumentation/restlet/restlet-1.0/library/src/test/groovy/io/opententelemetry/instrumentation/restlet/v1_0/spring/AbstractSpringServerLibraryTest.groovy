/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0.spring

import com.noelios.restlet.StatusFilter
import io.opentelemetry.instrumentation.restlet.v1_0.RestletTracing
import io.opentelemetry.instrumentation.restlet.v1_0.spring.AbstractSpringServerTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Restlet
import org.restlet.Route
import org.restlet.Router
import org.restlet.util.RouteList

abstract class AbstractSpringServerLibraryTest extends AbstractSpringServerTest implements LibraryTestTrait {
  @Override
  Restlet wrapRestlet(Restlet restlet, String path) {

    RestletTracing tracing = RestletTracing.newBuilder(openTelemetry)
      .captureHttpHeaders(capturedHttpHeadersForTesting())
      .build()

    def tracingFilter = tracing.newFilter(path)
    def statusFilter = new StatusFilter(component.getContext(), false, null, null)

    tracingFilter.setNext(statusFilter)
    statusFilter.setNext(restlet)

    return tracingFilter
  }

  @Override
  void setupRouting() {
    List<Route> routes = []
    for (Route route : router.getRoutes()) {
      def pattern = route.getTemplate().getPattern()
      routes.add(new Route(router, pattern, wrapRestlet(route.getNext(), pattern)))
    }
    router.setRoutes(new RouteList(routes))
    router.setDefaultRoute(new Route(router, "/", wrapRestlet(new Router(host.getContext()), "/*")))
    host.attach(router)
  }
}
