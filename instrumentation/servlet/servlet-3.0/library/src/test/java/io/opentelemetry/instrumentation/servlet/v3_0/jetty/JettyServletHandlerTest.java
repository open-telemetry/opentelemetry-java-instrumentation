/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.jetty;

import io.opentelemetry.instrumentation.servlet.v3_0.ServletTestUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyServletHandlerTest extends AbstractJettyServletHandlerTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configureServer(ServletHandler handler) {
    Filter filter = ServletTestUtil.newFilter(testing.getOpenTelemetry());
    handler.addFilterWithMapping(
        new FilterHolder(filter), "/*", EnumSet.allOf(DispatcherType.class));
  }

  @Override
  protected boolean isAgentTest() {
    return false;
  }
}
