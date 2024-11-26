/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_1.spring;

import static java.util.Collections.singletonList;

import com.noelios.restlet.StatusFilter;
import io.opentelemetry.instrumentation.restlet.v1_1.RestletTelemetry;
import io.opentelemetry.instrumentation.restlet.v1_1.spring.AbstractSpringServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.restlet.Filter;
import org.restlet.Restlet;
import org.restlet.Route;
import org.restlet.Router;
import org.restlet.util.RouteList;

abstract class AbstractSpringServerLibraryTest extends AbstractSpringServerTest {

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
    Filter statusFilter = new StatusFilter(component.getContext(), false, null, null);

    tracingFilter.setNext(statusFilter);
    statusFilter.setNext(restlet);

    return tracingFilter;
  }

  @Override
  protected void setupRouting() {
    List<Route> routes = new ArrayList<>();
    for (Route route : router.getRoutes()) {
      String pattern = route.getTemplate().getPattern();
      routes.add(new Route(router, pattern, wrapRestlet(route.getNext(), pattern)));
    }
    router.setRoutes(new RouteList(routes));
    router.setDefaultRoute(
        new Route(router, "/", wrapRestlet(new Router(host.getContext()), "/*")));
    host.attach(router);
  }
}
