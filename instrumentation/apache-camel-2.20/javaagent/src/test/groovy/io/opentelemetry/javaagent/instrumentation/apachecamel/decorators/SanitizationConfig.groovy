/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators

import org.apache.camel.builder.RouteBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class SanitizationConfig {

  @Bean
  RouteBuilder testRoute() {
    return new RouteBuilder() {
      @Override
      void configure() throws Exception {
      }
    }
  }

}
