/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base


import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM

abstract class SpringWebFluxServerTest extends HttpServerTest<ConfigurableApplicationContext> implements AgentTestTrait {
  protected abstract Class<?> getApplicationClass();

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(getApplicationClass())
    app.setDefaultProperties([
      "server.port"                 : port,
      "server.context-path"         : getContextPath(),
      "server.servlet.contextPath"  : getContextPath(),
      "server.error.include-message": "always"])
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return "/**"
      default:
        return super.expectedServerSpanName(endpoint)
    }
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    return true
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testConcurrency() {
    return true
  }

  @Override
  Class<?> expectedExceptionClass() {
    return IllegalStateException
  }
}
