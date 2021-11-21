/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.spring

import io.opentelemetry.instrumentation.restlet.v2_0.RestletTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Request
import org.restlet.Response
import org.restlet.Restlet
import org.restlet.data.Status
import org.restlet.engine.application.StatusFilter
import org.restlet.routing.Route
import org.restlet.routing.Template
import org.restlet.routing.TemplateRoute
import org.restlet.service.StatusService
import org.restlet.util.RouteList

import java.lang.reflect.Modifier

abstract class AbstractSpringServerLibraryTest extends AbstractSpringServerTest implements LibraryTestTrait {
  @Override
  Restlet wrapRestlet(Restlet restlet, String path) {

    RestletTracing tracing = RestletTracing.builder(openTelemetry)
      .setCaptureHttpHeaders(capturedHttpHeadersForTesting())
      .build()

    def tracingFilter = tracing.newFilter(path)
    def statusFilter = new StatusFilter(component.getContext(), new StatusService())

    tracingFilter.setNext(statusFilter)
    statusFilter.setNext(restlet)

    return tracingFilter
  }


  @Override
  void setupRouting() {
    //for latestDepTest
    def routeClass = Modifier.isAbstract(Route.getModifiers()) ? TemplateRoute : Route

    List<Route> routes = []
    for (Route route : router.getRoutes()) {
      def pattern = route.getTemplate().getPattern()
      routes.add((Route) routeClass.newInstance(router, pattern, wrapRestlet(route.getNext(), pattern)))
    }

    def notFoundRestlet = new Restlet(router.getContext()) {
      @Override
      void handle(Request request, Response response) {
        super.handle(request, response)
        response.setStatus(Status.CLIENT_ERROR_NOT_FOUND)
      }
    }
    notFoundRestlet = wrapRestlet(notFoundRestlet, "/*")

    def route = (Route) routeClass.newInstance(router, "/", notFoundRestlet)
    route.setMatchingMode(Template.MODE_STARTS_WITH)
    routes.add(route)

    router.setRoutes(new RouteList(routes))

    host.attach(router)

  }
}
