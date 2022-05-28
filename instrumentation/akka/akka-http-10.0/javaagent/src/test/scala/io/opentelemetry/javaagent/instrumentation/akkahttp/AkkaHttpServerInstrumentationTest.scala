package io.opentelemetry.javaagent.instrumentation.akkahttp

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{HttpServerInstrumentationExtension, HttpServerTestOptions}
import org.junit.jupiter.api.extension.RegisterExtension

class AkkaHttpServerInstrumentationTest extends AbstractHttpServerInstrumentationTest {
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
    //Akka HTTP actively prevents exceptions from reaching the controller through implicit handleException methods,
    // as unhandled exceptions reaching the HTTP controller would kill the entire HTTP server.
    options.setTestException(false)
  }
}


