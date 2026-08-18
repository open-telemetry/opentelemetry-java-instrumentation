/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import static java.util.Collections.singletonList;

import io.helidon.webserver.http.HttpRouting;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class HelidonServerTest extends AbstractHelidonTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  @SuppressWarnings("deprecation") // testing deprecated API
  protected void configureRoutes(HttpRouting.Builder routing) {
    var feature =
        HelidonTelemetry.builder(testing.getOpenTelemetry())
            // keeps coverage of the deprecated exact-name setter
            .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setResponseHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .build();
    routing.addFilter(feature.createFilter());
  }
}
