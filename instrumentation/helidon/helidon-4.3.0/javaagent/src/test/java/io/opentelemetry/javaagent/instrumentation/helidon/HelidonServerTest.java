/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.instrumentation.helidon.AbstractHelidonTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

class HelidonServerTest extends AbstractHelidonTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasResponseCustomizer(serverEndpoint -> true);
    options.setTestException(false);
  }
}
