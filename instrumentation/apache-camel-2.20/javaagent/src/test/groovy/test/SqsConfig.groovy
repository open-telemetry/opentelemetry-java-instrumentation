/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test


import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer

@SpringBootConfiguration
@EnableAutoConfiguration
class SqsConfig {

  @Bean
  RouteBuilder consumerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("aws-sqs://sqsCamelTest?amazonSQSClient=#sqsClient&messageAttributeNames=traceparent")
          .log(LoggingLevel.INFO, "test", "RECEIVER got body : \${body}")
          .log(LoggingLevel.INFO, "test", "RECEIVER got headers : \${headers}")
      }
    }
  }

  @Bean
  RouteBuilder producerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .log(LoggingLevel.INFO, "test", "SENDING body: \${body}")
          .log(LoggingLevel.INFO, "test", "SENDING headers: \${headers}")
          .to("aws-sqs://sqsCamelTest?amazonSQSClient=#sqsClient")
      }
    }
  }

  @Bean
  AmazonSQSAsync sqsClient(LocalStackContainer localstack) {

    return AmazonSQSAsyncClient.asyncBuilder().withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()
  }
}
