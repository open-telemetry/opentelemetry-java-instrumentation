/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v7_0.server;

import io.opentelemetry.instrumentation.spring.webflux.server.AbstractSpringWebfluxTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import server.SpringWebFluxTestApplication;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      SpringWebFluxTestApplication.class,
      SpringWebfluxTest.ForceNettyAutoConfiguration.class
    })
class SpringWebfluxTest extends AbstractSpringWebfluxTest {
  @TestConfiguration
  static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }
}
