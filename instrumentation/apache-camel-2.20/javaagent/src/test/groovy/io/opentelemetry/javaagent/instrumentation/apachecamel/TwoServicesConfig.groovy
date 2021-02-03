/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import org.apache.camel.builder.RouteBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class TwoServicesConfig {

  @Bean
  RouteBuilder serviceOneRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {

        from("undertow:http://0.0.0.0:{{service.one.port}}/serviceOne")
          .routeId("serviceOne")
          .streamCaching()
          .removeHeaders("CamelHttp*")
          .log("Service One request: \${body}")
          .delay(simple("\${random(1000,2000)}"))
          .transform(simple("Service-One-\${body}"))
          .to("http://0.0.0.0:{{service.two.port}}/serviceTwo")
          .log("Service One response: \${body}")
      }
    }
  }

  @Bean
  RouteBuilder serviceTwoRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {

        from("jetty:http://0.0.0.0:{{service.two.port}}/serviceTwo?arg=value")
          .routeId("serviceTwo")
          .streamCaching()
          .log("Service Two request: \${body}")
          .delay(simple("\${random(1000, 2000)}"))
          .transform(simple("Service-Two-\${body}"))
          .log("Service Two response: \${body}")
      }
    }
  }
}
