/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import org.junit.jupiter.api.extension.RegisterExtension

class AkkaHttpServerInstrumentationTestSync
    extends AbstractHttpServerInstrumentationTest {

  @RegisterExtension val _: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    AkkaHttpTestSyncWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    AkkaHttpTestSyncWebServer.stop()
}
