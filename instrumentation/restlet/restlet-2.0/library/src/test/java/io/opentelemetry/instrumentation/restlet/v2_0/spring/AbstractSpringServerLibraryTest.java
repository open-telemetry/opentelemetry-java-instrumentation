/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.spring;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.restlet.v2_0.RestletTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Status;
import org.restlet.engine.application.StatusFilter;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.routing.TemplateRoute;
import org.restlet.service.StatusService;
import org.restlet.util.RouteList;

abstract class AbstractSpringServerLibraryTest extends AbstractSpringServerTest {

  // org.restlet.routing.Route is deprecated in 2.0 but not deprecated in later versions
  @SuppressWarnings("deprecation")
  private static final Class<?> ROUTE_CLASS = org.restlet.routing.Route.class;

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected Restlet wrapRestlet(Restlet restlet, String path) {
    RestletTelemetry telemetry =
        RestletTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build();

    Filter tracingFilter = telemetry.newFilter(path);
    Filter statusFilter = new StatusFilter(component.getContext(), new StatusService());

    tracingFilter.setNext(statusFilter);
    statusFilter.setNext(restlet);

    return tracingFilter;
  }

  // org.restlet.routing.Route is deprecated in 2.0 but not deprecated in later versions
  @SuppressWarnings("deprecation")
  @Override
  protected void setupRouting() {
    try {
      // for latestDepTest
      Class<?> routeClass =
          Modifier.isAbstract(ROUTE_CLASS.getModifiers()) ? TemplateRoute.class : ROUTE_CLASS;
      Constructor<?> routeConstructor =
          routeClass.getConstructor(Router.class, String.class, Restlet.class);
      Method getTemplate = routeClass.getMethod("getTemplate");

      List<org.restlet.routing.Route> routes = new ArrayList<>();
      for (org.restlet.routing.Route route : router.getRoutes()) {
        String pattern = ((Template) getTemplate.invoke(route)).getPattern();
        routes.add(
            (org.restlet.routing.Route)
                routeConstructor.newInstance(
                    router, pattern, wrapRestlet(route.getNext(), pattern)));
      }

      Restlet notFoundRestlet =
          new Restlet(router.getContext()) {
            @Override
            public void handle(Request request, Response response) {
              super.handle(request, response);
              response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            }
          };
      notFoundRestlet = wrapRestlet(notFoundRestlet, "/*");

      org.restlet.routing.Route route =
          (org.restlet.routing.Route) routeConstructor.newInstance(router, "/", notFoundRestlet);
      ((Template) getTemplate.invoke(route)).setMatchingMode(Template.MODE_STARTS_WITH);
      routes.add(route);

      router.setRoutes(new RouteList(routes));
      host.attach(router);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
