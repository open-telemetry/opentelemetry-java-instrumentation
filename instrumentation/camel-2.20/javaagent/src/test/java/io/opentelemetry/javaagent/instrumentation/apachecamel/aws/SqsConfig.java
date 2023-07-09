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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
class SqsConfig {

  @Bean
  @ConditionalOnProperty("queueName")
  RouteBuilder consumerRoute(@Value("${queueName}") String queueName) {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("aws-sqs://" + queueName + "?amazonSQSClient=#sqsClient&delay=1000")
            .log(LoggingLevel.INFO, "test-consumer", "RECEIVER got body : ${body}")
            .log(LoggingLevel.INFO, "test-consumer", "RECEIVER got headers : ${headers}");
      }
    };
  }

  @Bean
  @ConditionalOnProperty("queueName")
  RouteBuilder producerRoute(@Value("${queueName}") String queueName) {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("direct:input")
            .log(LoggingLevel.INFO, "test-producer", "SENDING body: ${body}")
            .log(LoggingLevel.INFO, "test-producer", "SENDING headers: ${headers}")
            .to("aws-sqs://" + queueName + "?amazonSQSClient=#sqsClient&delay=1000");
      }
    };
  }

  @Bean
  @ConditionalOnProperty("queueSdkConsumerName")
  RouteBuilder producerRouteForSdkConsumer(
      @Value("${queueSdkConsumerName}") String queueSdkConsumerName) {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("direct:inputSdkConsumer")
            .log(LoggingLevel.INFO, "test", "SENDING body: ${body}")
            .log(LoggingLevel.INFO, "test", "SENDING headers: ${headers}")
            .to("aws-sqs://" + queueSdkConsumerName + "?amazonSQSClient=#sqsClient&delay=1000");
      }
    };
  }
}
