/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions
}
import org.junit.jupiter.api.extension.RegisterExtension

class AkkaHttpServerInstrumentationTest
    extends AbstractHttpServerInstrumentationTest {
  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    AkkaHttpTestWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    AkkaHttpTestWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options)
    // exception doesn't propagate
    options.setTestException(false)
  }
}
