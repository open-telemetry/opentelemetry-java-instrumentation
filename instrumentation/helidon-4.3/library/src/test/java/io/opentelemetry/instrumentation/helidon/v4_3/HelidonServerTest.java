/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import io.helidon.webserver.http.HttpRouting;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class HelidonServerTest extends AbstractHelidonTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configureRoutes(HttpRouting.Builder routing) {
    var feature =
        HelidonTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .build();
    routing.addFilter(feature.createFilter());
  }
}
