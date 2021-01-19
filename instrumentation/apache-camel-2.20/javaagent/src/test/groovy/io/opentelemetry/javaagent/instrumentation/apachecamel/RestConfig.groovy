/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class RestConfig {

  @Bean
  RouteBuilder routes() {
    return new RouteBuilder() {
      @Override
      void configure() throws Exception {

        restConfiguration()
          .component("jetty")
          .bindingMode(RestBindingMode.auto)
          .host("localhost")
          .port("{{restServer.port}}")
          .producerComponent("http")

        rest("/api")
          .get("/{module}/unit/{unitId}")
          .to("direct:moduleUnit")

        from("direct:moduleUnit")
          .transform().simple("\${header.unitId} of \${header.module}")

        // producer - client route
        from("direct:start")
          .log(LoggingLevel.INFO, "test", "SENDING request")
          .to("rest:get:api/{module}/unit/{unitId}")
          .log(LoggingLevel.INFO, "test", "RECEIVED response: '\${body}'")
      }
    }
  }
}
