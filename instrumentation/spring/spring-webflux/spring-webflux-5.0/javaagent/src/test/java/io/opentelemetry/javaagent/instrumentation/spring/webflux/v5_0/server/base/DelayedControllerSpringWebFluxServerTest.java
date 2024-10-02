/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Tests the case which uses annotated controller methods, and where "controller" span is created
 * within a Mono map step, which follows a delay step. For exception endpoint, the exception is
 * thrown within the last map step.
 */
public class DelayedControllerSpringWebFluxServerTest extends ControllerSpringWebFluxServerTest {
  @Override
  protected Class<?> getApplicationClass() {
    return Application.class;
  }

  @Configuration
  @EnableAutoConfiguration
  static class Application {
    @Bean
    Controller controller() {
      return new Controller();
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  @RestController
  static class Controller extends ServerTestController {
    @Override
    protected <T> Mono<T> wrapControllerMethod(ServerEndpoint endpoint, Supplier<T> handler) {
      return Mono.just("")
          .delayElement(Duration.ofMillis(10))
          .map(unused -> controller(endpoint, handler::get));
    }
  }
}
