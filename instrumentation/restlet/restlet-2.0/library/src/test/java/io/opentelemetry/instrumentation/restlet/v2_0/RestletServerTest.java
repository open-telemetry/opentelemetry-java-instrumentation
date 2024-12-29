/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.restlet.Restlet;
import org.restlet.engine.application.StatusFilter;
import org.restlet.routing.Filter;
import org.restlet.service.StatusService;

class RestletServerTest extends AbstractRestletServerTest {

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
}
