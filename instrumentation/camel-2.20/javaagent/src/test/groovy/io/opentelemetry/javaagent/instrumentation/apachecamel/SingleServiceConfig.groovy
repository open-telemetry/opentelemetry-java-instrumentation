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
class SingleServiceConfig {

  @Bean
  RouteBuilder serviceRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {

        from("undertow:http://0.0.0.0:{{camelService.port}}/camelService")
          .routeId("camelService")
          .streamCaching()
          .log("CamelService request: \${body}")
          .delay(simple("\${random(1000, 2000)}"))
          .transform(simple("CamelService-\${body}"))
          .log(LoggingLevel.INFO, "test", "CamelService response: \${body}")
      }
    }
  }
}
