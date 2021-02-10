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
class DirectConfig {

  @Bean
  RouteBuilder receiverRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:receiver")
          .log(LoggingLevel.INFO, "test", "RECEIVER got: \${body}")
          .delay(simple("2000"))
          .setBody(constant("result"))
      }
    }
  }

  @Bean
  RouteBuilder clientRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .log(LoggingLevel.INFO, "test", "SENDING request \${body}")
          .to("direct:receiver")
          .log(LoggingLevel.INFO, "test", "RECEIVED response \${body}")
      }
    }
  }
}
