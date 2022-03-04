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

/**
 * Tests the case where "controller" span is created within the route handler method scope, and
 * the Mono<ServerResponse> from a handler is already a fully constructed response with no deferred
 * actions. For exception endpoint, the exception is thrown within route handler method scope.
 */
class ImmediateHandlerSpringWebFluxServerTest extends HandlerSpringWebFluxServerTest {
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
      return controller(endpoint, {
        spanAction.run()
        return response
      })
    }
  }
}
