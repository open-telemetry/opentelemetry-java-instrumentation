/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackRoutesTest;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.server.RatpackServerSpec;

class RatpackRoutesTest extends AbstractRatpackRoutesTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(RatpackServerSpec serverSpec) throws Exception {
    RatpackServerTelemetry telemetry = RatpackServerTelemetry.create(testing.getOpenTelemetry());
    serverSpec.registryOf(telemetry::configureRegistry);
  }

  @Override
  protected boolean hasHandlerSpan() {
    return false;
  }
}
