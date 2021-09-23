/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class SqsCamelTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.elasticMq()

  def cleanupSpec() {
    awsConnector.disconnect()
  }

  def "camel SQS producer - camel SQS consumer"() {
    setup:
    String queueName = "sqsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SqsConfig, ImmutableMap.of("queueName", queueName))
    def queueUrl = awsConnector.createQueue(queueName)

    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(4) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.ListQueues")
      }
      trace(1, 5) {
        CamelSpan.direct(it, 0, "input")
        CamelSpan.sqsProduce(it, 1, queueName, span(0))
        AwsSpan.sqs(it, 2, "SQS.SendMessage", queueUrl, null, PRODUCER, span(1))
        AwsSpan.sqs(it, 3, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(2))
        CamelSpan.sqsConsume(it, 4, queueName, span(2))
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.DeleteMessage", queueUrl)
      }
    }
    cleanup:
    camelApp.stop()
  }

  def "AWS SDK SQS producer - camel SQS consumer"() {
    setup:
    String queueName = "sqsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SqsConfig, ImmutableMap.of("queueName", queueName))
    def queueUrl = awsConnector.createQueue(queueName)

    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    awsConnector.sendSampleMessage(queueUrl)

    then:
    assertTraces(5) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.ListQueues")
      }
      trace(1, 3) {
        AwsSpan.sqs(it, 0, "SQS.SendMessage", queueUrl, null, PRODUCER)
        AwsSpan.sqs(it, 1, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(0))
        CamelSpan.sqsConsume(it, 2, queueName, span(0))
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.DeleteMessage", queueUrl)
      }
      trace(4, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    cleanup:
    camelApp.stop()
  }

  def "camel SQS producer - AWS SDK SQS consumer"() {
    setup:
    String queueName = "sqsCamelTestSdkConsumer"
    def camelApp = new CamelSpringApp(awsConnector, SqsConfig, ImmutableMap.of("queueSdkConsumerName", queueName))
    def queueUrl = awsConnector.createQueue(queueName)

    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    camelApp.producerTemplate().sendBody("direct:inputSdkConsumer", "{\"type\": \"hello\"}")
    awsConnector.receiveMessage(queueUrl)

    then:
    assertTraces(3) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.ListQueues")
      }
      trace(1, 4) {
        CamelSpan.direct(it, 0, "inputSdkConsumer")
        CamelSpan.sqsProduce(it, 1, queueName, span(0))
        AwsSpan.sqs(it, 2, "SQS.SendMessage", queueUrl, null, PRODUCER, span(1))
        AwsSpan.sqs(it, 3, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(2))
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    cleanup:
    camelApp.stop()
  }

  def waitAndClearSetupTraces(queueUrl, queueName) {
    assertTraces(1) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.CreateQueue", queueUrl, queueName)
      }
    }
    clearExportedData()
  }
}
