/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackAsyncHttpServerTest;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.server.RatpackServerSpec;

class RatpackAsyncHttpServerTest extends AbstractRatpackAsyncHttpServerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configure(RatpackServerSpec serverSpec) throws Exception {
    RatpackServerTelemetry telemetry =
        RatpackServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build();
    serverSpec.registryOf(telemetry::configureRegistry);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasHandlerSpan(endpoint -> false);
  }
}
