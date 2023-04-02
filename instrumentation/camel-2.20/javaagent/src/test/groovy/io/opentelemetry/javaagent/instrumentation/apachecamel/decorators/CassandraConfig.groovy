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
class CassandraConfig {

  @Bean
  RouteBuilder serviceRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .setHeader("CamelCqlQuery", simple("select * from test.users where id=1 ALLOW FILTERING"))
          .toD("cql://{{cassandra.host}}:{{cassandra.port}}/test")
      }
    }
  }
}
