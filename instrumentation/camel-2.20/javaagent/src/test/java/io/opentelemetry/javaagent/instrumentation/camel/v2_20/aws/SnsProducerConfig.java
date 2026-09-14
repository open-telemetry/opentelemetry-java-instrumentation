/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.aws;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
class SnsProducerConfig {

  @Bean
  RouteBuilder snsProducerRoute(@Value("${topicName}") String topicName) {
    return new RouteBuilder() {
      @Override
      public void configure() {
        from("direct:input").to("aws-sns://" + topicName + "?amazonSNSClient=#snsClient");
      }
    };
  }
}
