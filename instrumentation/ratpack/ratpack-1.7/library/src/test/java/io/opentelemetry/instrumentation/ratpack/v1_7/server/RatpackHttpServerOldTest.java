/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.server.RatpackServerSpec;

@SuppressWarnings("deprecation") // testing deprecated API
class RatpackHttpServerOldTest extends AbstractRatpackHttpServerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configure(RatpackServerSpec serverSpec) throws Exception {
    io.opentelemetry.instrumentation.ratpack.v1_7.RatpackTelemetry telemetry =
        io.opentelemetry.instrumentation.ratpack.v1_7.RatpackTelemetry.builder(
                testing.getOpenTelemetry())
            .setCapturedServerRequestHeaders(
                singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedServerResponseHeaders(
                singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build();
    serverSpec.registryOf(telemetry::configureServerRegistry);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasHandlerSpan(endpoint -> false);
  }
}
