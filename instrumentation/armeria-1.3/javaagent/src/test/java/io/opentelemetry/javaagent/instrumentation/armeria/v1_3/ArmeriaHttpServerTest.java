/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ServerBuilder configureServer(ServerBuilder sb) {
    return sb;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasResponseCustomizer(
        endpoint -> ServerEndpoint.NOT_FOUND != endpoint && ServerEndpoint.EXCEPTION != endpoint);
    options.setTestHttpPipelining(false);
    // span for non-standard request is created by netty instrumentation when not running latest
    // dep tests
    if (Boolean.getBoolean("testLatestDeps")) {
      options.disableTestNonStandardHttpMethod();
    } else {
      options.setResponseCodeOnNonStandardHttpMethod(405);
    }
  }
}
