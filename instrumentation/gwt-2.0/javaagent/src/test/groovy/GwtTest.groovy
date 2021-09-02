/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.openqa.selenium.firefox.FirefoxOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class GwtTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {
  private static final Logger logger = LoggerFactory.getLogger(GwtTest)

  @Shared
  BrowserWebDriverContainer<?> browser

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    webAppContext.setBaseResource(Resource.newResource(new File("build/testapp/web")))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  def setupSpec() {
    Testcontainers.exposeHostPorts(port)

    browser = new BrowserWebDriverContainer<>()
      .withCapabilities(new FirefoxOptions())
      .withLogConsumer(new Slf4jLogConsumer(logger))
    browser.start()

    address = new URI("http://host.testcontainers.internal:$port" + getContextPath() + "/")
  }

  def cleanupSpec() {
    browser?.stop()
  }

  @Override
  String getContextPath() {
    return "/xyz"
  }

  def getDriver() {
    def driver = browser.getWebDriver()
    driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS)
    return driver
  }

  def "test gwt"() {
    setup:
    def driver = getDriver()

    // fetch the test page
    driver.get(address.resolve("greeting.html").toString())

    expect:
    // wait for page to load
    driver.findElementByClassName("greeting.button")
    assertTraces(4) {
      traces.sort(orderByRootSpanName("/*", "HTTP GET"))

      // /xyz/greeting.html
      trace(0, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/greeting/greeting.nocache.js
      trace(1, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/greeting/1B105441581A8F41E49D5DF3FB5B55BA.cache.html
      trace(2, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /favicon.ico
      trace(3, 1) {
        serverSpan(it, 0, "HTTP GET")
      }
    }
    clearExportedData()

    when:
    // click a button to trigger calling java code
    driver.findElementByClassName("greeting.button").click()

    then:
    // wait for response
    "Hello, Otel" == driver.findElementByClassName("message.received").getText()
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/greeting/greet")
        span(1) {
          name "test.gwt.shared.MessageService/sendMessage"
          kind SpanKind.INTERNAL
          childOf(span(0))
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "gwt"
            "${SemanticAttributes.RPC_SERVICE.key}" "test.gwt.shared.MessageService"
            "${SemanticAttributes.RPC_METHOD.key}" "sendMessage"
          }
        }
      }
    }
    clearExportedData()

    when:
    // click a button to trigger calling java code
    driver.findElementByClassName("error.button").click()

    then:
    // wait for response
    "Error" == driver.findElementByClassName("error.received").getText()
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/greeting/greet")
        span(1) {
          name "test.gwt.shared.MessageService/sendMessage"
          kind SpanKind.INTERNAL
          childOf(span(0))
          errorEvent(IOException)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "gwt"
            "${SemanticAttributes.RPC_SERVICE.key}" "test.gwt.shared.MessageService"
            "${SemanticAttributes.RPC_METHOD.key}" "sendMessage"
          }
        }
      }
    }

    cleanup:
    driver.close()
  }

  static serverSpan(TraceAssert trace, int index, String spanName) {
    trace.span(index) {
      hasNoParent()

      name spanName
      kind SpanKind.SERVER
    }
  }
}
