/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.aws.s3.S3Constants
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class S3Config {

  @Bean
  RouteBuilder sqsDirectlyFromS3ConsumerRoute(@Value("\${queueName}") String queueName) {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("aws-sqs://${queueName}?amazonSQSClient=#sqsClient&delay=1000")
          .log(LoggingLevel.INFO, "test", "RECEIVER got body : \${body}")
          .log(LoggingLevel.INFO, "test", "RECEIVER got headers : \${headers}")
      }
    }
  }

  @Bean
  RouteBuilder s3ToSqsProducerRoute(@Value("\${bucketName}") String bucketName) {
    return new RouteBuilder() {

      @Override
      void configure() throws Exception {
        from("direct:input")
          .log(LoggingLevel.INFO, "test", "SENDING body: \${body}")
          .log(LoggingLevel.INFO, "test", "SENDING headers: \${headers}")
          .convertBodyTo(byte[].class)
          .setHeader(S3Constants.KEY, simple("test-data"))
          .to("aws-s3://${bucketName}?amazonS3Client=#s3Client")
      }
    }
  }
}
