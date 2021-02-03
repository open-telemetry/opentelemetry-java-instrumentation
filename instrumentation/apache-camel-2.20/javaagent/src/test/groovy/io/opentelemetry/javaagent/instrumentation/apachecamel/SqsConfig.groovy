/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class SqsConfig {

  @Bean
  RouteBuilder consumerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("aws-sqs://sqsCamelTest?amazonSQSClient=#sqsClient")
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
  RouteBuilder separateQueueProducerRoute() {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:separate-input")
          .log(LoggingLevel.INFO, "test", "SENDING body: \${body}")
          .log(LoggingLevel.INFO, "test", "SENDING headers: \${headers}")
          .to("aws-sqs://sqsCamelSeparateQueueTest?amazonSQSClient=#sqsClient")
      }
    }
  }

  /**
   * Temporarily using emq instead of localstack till the latter supports AWS trace propagation
   *
  @Bean
  AmazonSQSAsync sqsClient(LocalStackContainer localstack) {

    return AmazonSQSAsyncClient.asyncBuilder().withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()
  }**/

  @Bean
  AmazonSQSAsync sqsClient(@Value("\${sqs.port}") int port) {
    def credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"))
    def endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:"+port, "elasticmq")
    return AmazonSQSAsyncClient.asyncBuilder().withCredentials(credentials).withEndpointConfiguration(endpointConfiguration).build()
  }
}
