/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1;

import io.opentelemetry.instrumentation.restlet.v1_1.AbstractServletServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

class ServletServerTest extends AbstractServletServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasResponseCustomizer((endpoint) -> true);
  }
}
