/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class MulticastConfig {

  @Bean
  RouteBuilder firstServiceRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:first")
          .log(LoggingLevel.INFO, "test", "FIRST request: \${body}")
          .delay(simple("1000"))
          .setBody(constant("first"))
      }
    }
  }

  @Bean
  RouteBuilder secondServiceRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:second")
          .log(LoggingLevel.INFO, "test", "SECOND request: \${body}")
          .delay(simple("2000"))
          .setBody(constant("second"))
      }
    }
  }

  @Bean
  RouteBuilder clientServiceRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .log(LoggingLevel.INFO, "test", "SENDING request \${body}")
          .multicast()
          .parallelProcessing()
          .to("direct:first", "direct:second")
          .end()
          .log(LoggingLevel.INFO, "test", "RECEIVED response \${body}")
      }
    }
  }
}
