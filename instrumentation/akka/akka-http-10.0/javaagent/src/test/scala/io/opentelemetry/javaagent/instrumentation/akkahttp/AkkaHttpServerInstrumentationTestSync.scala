/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions
}
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

class AkkaHttpServerInstrumentationTestSync
    extends AbstractHttpServerInstrumentationTest {

  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    AkkaHttpTestSyncWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    AkkaHttpTestSyncWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options)
    // FIXME: latest deps does not fill http.status_code
    options.setTestException(!java.lang.Boolean.getBoolean("testLatestDeps"))
  }
}
