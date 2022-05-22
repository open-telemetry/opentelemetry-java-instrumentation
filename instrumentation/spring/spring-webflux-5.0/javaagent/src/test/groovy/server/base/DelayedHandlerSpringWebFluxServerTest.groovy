/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base


import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

import java.time.Duration

/**
 * Tests the case which uses route handlers, and where "controller" span is created within a Mono
 * map step, which follows a delay step. For exception endpoint, the exception is thrown within the
 * last map step.
 */
class DelayedHandlerSpringWebFluxServerTest extends HandlerSpringWebFluxServerTest {
  @Override
  protected Class<?> getApplicationClass() {
    return Application
  }

  @Configuration
  @EnableAutoConfiguration
  static class Application {
    @Bean
    RouterFunction<ServerResponse> router() {
      return new RouteFactory().createRoutes()
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory()
    }
  }

  static class RouteFactory extends ServerTestRouteFactory {

    @Override
    protected Mono<ServerResponse> wrapResponse(ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction) {
      return response.delayElement(Duration.ofMillis(10)).map({ original ->
        return controller(endpoint, {
          spanAction.run()
          return original
        })
      })
    }
  }

  @Override
  boolean hasHandlerAsControllerParentSpan(ServerEndpoint endpoint) {
    return false
  }
}
