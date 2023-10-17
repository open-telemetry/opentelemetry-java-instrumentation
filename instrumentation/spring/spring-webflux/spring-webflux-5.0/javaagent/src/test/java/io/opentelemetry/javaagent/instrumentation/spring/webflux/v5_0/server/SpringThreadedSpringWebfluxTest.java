/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.spring.webflux.IpcSingleThreadNettyCustomizer;
import io.opentelemetry.instrumentation.spring.webflux.server.SingleThreadNettyCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
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
      factory.addServerCustomizers(customizer());
      return factory;
    }
  }

  static NettyServerCustomizer customizer() {
    if (Boolean.getBoolean("testLatestDeps")) {
      return new SingleThreadNettyCustomizer();
    }
    return new IpcSingleThreadNettyCustomizer();
  }
}
