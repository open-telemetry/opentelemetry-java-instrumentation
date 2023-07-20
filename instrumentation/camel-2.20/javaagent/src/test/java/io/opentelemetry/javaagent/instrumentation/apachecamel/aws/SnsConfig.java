/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
class SnsConfig {

  @Bean
  RouteBuilder sqsConsumerRoute(@Value("${queueName}") String queueName) {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("aws-sqs://" + queueName + "?amazonSQSClient=#sqsClient&delay=1000")
            .log(LoggingLevel.INFO, "test-sqs", "RECEIVER got body : ${body}")
            .log(LoggingLevel.INFO, "test-sqs", "RECEIVER got headers : ${headers}");
      }
    };
  }

  @Bean
  RouteBuilder snsProducerRoute(@Value("${topicName}") String topicName) {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("direct:input")
            .log(LoggingLevel.INFO, "test-sns", "SENDING body: ${body}")
            .log(LoggingLevel.INFO, "test-sns", "SENDING headers: ${headers}")
            .to("aws-sns://" + topicName + "?amazonSNSClient=#snsClient");
      }
    };
  }
}
