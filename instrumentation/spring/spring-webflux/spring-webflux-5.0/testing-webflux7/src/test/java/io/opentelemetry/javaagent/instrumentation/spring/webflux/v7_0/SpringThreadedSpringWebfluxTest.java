/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v7_0;

import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.netty.resources.LoopResources;
import server.SpringWebFluxTestApplication;

/**
 * Run all Webflux tests under netty event loop having only 1 thread. Some of the bugs are better
 * visible in this setup because same thread is reused for different requests.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      SpringWebFluxTestApplication.class,
      SpringThreadedSpringWebfluxTest.ForceSingleThreadedNettyAutoConfiguration.class
    })
class SpringThreadedSpringWebfluxTest extends SpringWebfluxTest {

  @TestConfiguration
  static class ForceSingleThreadedNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
      // Configure single-threaded event loop for Spring Boot 4
      factory.addServerCustomizers(
          server -> server.runOn(LoopResources.create("my-http", 1, true)));
      return factory;
    }
  }
}
