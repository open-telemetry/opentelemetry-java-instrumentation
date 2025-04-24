/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon;

import io.helidon.webserver.http.HttpRouting;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;

class HelidonServerTest extends AbstractHelidonTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configureContexts(HttpRouting.Builder routing) {

    var feature =
        HelidonTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build();
    routing.addFeature(feature);
  }
}
