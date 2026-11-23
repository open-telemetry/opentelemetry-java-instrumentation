/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import io.opentelemetry.instrumentation.servlet.v3_0.tomcat.BaseTomcatDispatchTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class Tomcat9DispatchTest extends BaseTomcatDispatchTest {
  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  static void bothServletNamespacesAreAvailable() throws ClassNotFoundException {
    Class.forName("javax.servlet.ServletResponse");
    Class.forName("jakarta.servlet.ServletResponse");
  }
}
