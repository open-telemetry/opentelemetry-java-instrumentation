/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty;

import io.opentelemetry.instrumentation.servlet.v3_0.jetty.BaseJettyDispatchTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyDispatchTest extends BaseJettyDispatchTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();
}
