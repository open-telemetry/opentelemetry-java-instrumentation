/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions,
  ServerEndpoint
}
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.function.BiFunction

class PekkoHttpServerSourceInstrumentationTest
    extends AbstractHttpServerInstrumentationTest {

  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    PekkoHttpTestServerSourceWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    PekkoHttpTestServerSourceWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options, hasRoute = true)
    options.setTestException(false)
    options.setTestPathParam(true)

    options.setExpectedHttpRoute(
      new BiFunction[ServerEndpoint, String, String] {
        override def apply(
            endpoint: ServerEndpoint,
            method: String
        ): String =
          if (endpoint eq ServerEndpoint.PATH_PARAM) "/path/*/param"
          else expectedHttpRoute(endpoint, method)
      }
    )
  }
}
