/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_5.spring;

import io.opentelemetry.instrumentation.restlet.v2_5.RestletTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Status;
import org.restlet.engine.application.StatusFilter;
import org.restlet.routing.Filter;
import org.restlet.routing.Template;
import org.restlet.routing.TemplateRoute;
import org.restlet.service.StatusService;

abstract class AbstractSpringServerLibraryTest extends AbstractSpringServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected Restlet wrapRestlet(Restlet restlet, String path) {
    RestletTelemetry telemetry =
        RestletTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(AbstractHttpServerTest.TEST_HEADERS)
            .setResponseHeaders(AbstractHttpServerTest.TEST_HEADERS)
            .build();

    Filter tracingFilter = telemetry.createFilter(path);
    Filter statusFilter = new StatusFilter(component.getContext(), new StatusService());

    tracingFilter.setNext(statusFilter);
    statusFilter.setNext(restlet);

    return tracingFilter;
  }

  @Override
  protected void setupRouting() {
    router
        .getRoutes()
        .forEach(route -> {
          String pattern = ((TemplateRoute) route).getTemplate().getPattern();
          route.setNext(wrapRestlet(route.getNext(), pattern));
        });

    Restlet notFoundRestlet =
        new Restlet(router.getContext()) {
          @Override
          public void handle(Request request, Response response) {
            super.handle(request, response);
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
          }
        };
    router
        .attach("/", wrapRestlet(notFoundRestlet, "/*"))
        .getTemplate()
        .setMatchingMode(Template.MODE_STARTS_WITH);
    host.attach(router);
  }
}
