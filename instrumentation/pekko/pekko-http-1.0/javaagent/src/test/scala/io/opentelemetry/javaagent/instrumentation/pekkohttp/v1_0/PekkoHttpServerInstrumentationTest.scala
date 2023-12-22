/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions
}
import org.junit.jupiter.api.extension.RegisterExtension

class PekkoHttpServerInstrumentationTest
    extends AbstractHttpServerInstrumentationTest {
  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    PekkoHttpTestWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    PekkoHttpTestWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options)
    // exception doesn't propagate
    options.setTestException(false)
  }
}
