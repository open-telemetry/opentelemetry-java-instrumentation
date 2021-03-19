/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.AmazonSQSClient
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@SpringBootConfiguration
@EnableAutoConfiguration
class SnsConfig {

  @Bean
  LocalStackContainer localstack() {
    LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
    localstack.start()
    return localstack
  }

  @Bean
  RouteBuilder sqsCamelOnlyConsumerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("aws-sqs://snsCamelTest?amazonSQSClient=#sqsClient")
          .log(LoggingLevel.INFO, "test", "RECEIVER got body : \${body}")
          .log(LoggingLevel.INFO, "test", "RECEIVER got headers : \${headers}")
      }
    }
  }

  @Bean
  RouteBuilder snsProducerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .log(LoggingLevel.INFO, "test", "SENDING body: \${body}")
          .log(LoggingLevel.INFO, "test", "SENDING headers: \${headers}")
          .to("aws-sns://snsCamelTest?amazonSNSClient=#snsClient")
      }
    }
  }

  @Bean
  AmazonSQSClient sqsClient(LocalStackContainer localstack) {
    return AmazonSQSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()
  }

  @Bean
  AmazonSNSClient snsClient(LocalStackContainer localstack) {
    return AmazonSNSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SNS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()
  }
}
