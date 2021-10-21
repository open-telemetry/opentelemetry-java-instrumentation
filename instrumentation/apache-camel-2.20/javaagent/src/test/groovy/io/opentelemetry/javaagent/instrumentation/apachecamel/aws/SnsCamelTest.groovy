/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Ignore
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CONSUMER

@Ignore("Does not work with localstack - X-Ray features needed")
class SnsCamelTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.liveAws()

  def "AWS SDK SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SnsConfig, ImmutableMap.of("topicName", topicName, "queueName", queueName))

    // TODO: def (queueUrl, topicArn) fails to compile, switch back when this is fixed in spock
    // def (queueUrl, topicArn) = setupTestInfrastructure(queueName, topicName)
    Tuple tuple = setupTestInfrastructure(queueName, topicName)
    def queueUrl = tuple.get(0)
    def topicArn = tuple.get(1)
    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    awsConnector.publishSampleNotification(topicArn)

    then:
    assertTraces(4) {
      trace(0, 3) {
        AwsSpan.sns(it, 0, "SNS.Publish")
        AwsSpan.sqs(it, 1, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(0))
        CamelSpan.sqsConsume(it, 2, queueName, span(0))
      }
      // http client span
      trace(1, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.DeleteMessage", queueUrl)
      }
      // camel polling
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    cleanup:
    awsConnector.purgeQueue(queueUrl)
    camelApp.stop()
  }

  def "camel SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SnsConfig, ImmutableMap.of("topicName", topicName, "queueName", queueName))

    // TODO: def (queueUrl, topicArn) fails to compile, switch back when this is fixed in spock
    // def (queueUrl, topicArn) = setupTestInfrastructure(queueName, topicName)
    Tuple tuple = setupTestInfrastructure(queueName, topicName)
    def queueUrl = tuple.get(0)
    def topicArn = tuple.get(1)
    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assert topicArn != null
    assertTraces(4) {
      trace(0, 5) {
        CamelSpan.direct(it, 0, "input")
        CamelSpan.snsPublish(it, 1, topicName, span(0))
        AwsSpan.sns(it, 2, "SNS.Publish", span(1))
        AwsSpan.sqs(it, 3, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(2))
        CamelSpan.sqsConsume(it, 4, queueName, span(2))
      }
      trace(1, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.DeleteMessage", queueUrl)
      }
      // camel polling
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    cleanup:
    awsConnector.purgeQueue(queueUrl)
    camelApp.stop()
  }

  def setupTestInfrastructure(queueName, topicName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName)
    String queueArn = awsConnector.getQueueArn(queueName)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn)

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl)

    return [queueUrl, topicArn]
  }

  def waitAndClearSetupTraces(queueUrl, queueName) {
    assertTraces(6) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.CreateQueue", queueUrl, queueName)
      }
      trace(1, 1) {
        AwsSpan.sqs(it, 0, "SQS.GetQueueAttributes", queueUrl)
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.SetQueueAttributes", queueUrl)
      }
      trace(3, 1) {
        AwsSpan.sns(it, 0, "SNS.CreateTopic")
      }
      trace(4, 1) {
        AwsSpan.sns(it, 0, "SNS.Subscribe")
      }
      // test message
      trace(5, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    clearExportedData()
  }
}

