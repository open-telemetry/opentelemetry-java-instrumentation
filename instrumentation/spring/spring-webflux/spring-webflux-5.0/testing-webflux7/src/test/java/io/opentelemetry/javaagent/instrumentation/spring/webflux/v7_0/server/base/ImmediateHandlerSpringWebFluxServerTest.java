/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v7_0.server.base;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Tests the case where {@code Mono<ServerResponse>} from a router function is already a fully
 * constructed response with no deferred actions. For exception endpoint, the exception is thrown
 * within router function scope.
 */
class ImmediateHandlerSpringWebFluxServerTest extends HandlerSpringWebFluxServerTest {
  @Override
  protected Class<?> getApplicationClass() {
    return Application.class;
  }

  @Configuration
  @EnableAutoConfiguration
  static class Application {

    @Bean
    RouterFunction<ServerResponse> router() {
      return new RouteFactory().createRoutes();
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  static class RouteFactory extends ServerTestRouteFactory {
    @Override
    protected Mono<ServerResponse> wrapResponse(
        ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction) {
      spanAction.run();
      return response;
    }
  }
}
