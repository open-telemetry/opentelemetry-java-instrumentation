/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.tomcat;

import io.opentelemetry.instrumentation.servlet.v3_0.ServletTestUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.apache.catalina.Context;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatDispatchTest extends BaseTomcatDispatchTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configureServer(Context servletContext) {
    ServletTestUtil.configureTomcat(testing.getOpenTelemetry(), servletContext);
  }

  @Override
  protected boolean isAgentTest() {
    return false;
  }
}
