/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v7_0.server.base;

import io.opentelemetry.instrumentation.spring.webflux.server.AbstractHandlerSpringWebFluxServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.time.Duration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Tests the case which uses route handlers, and where "controller" span is created within a Mono
 * map step, which follows a delay step. For exception endpoint, the exception is thrown within the
 * last map step.
 */
class DelayedHandlerSpringWebFluxServerTest extends AbstractHandlerSpringWebFluxServerTest {
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
      return response
          .delayElement(Duration.ofMillis(10))
          .map(
              original -> {
                System.out.println("In wrapResponse map for endpoint " + endpoint);
                return controller(
                    endpoint,
                    () -> {
                      spanAction.run();
                      return original;
                    });
              });
    }
  }
}
