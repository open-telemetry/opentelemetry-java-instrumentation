/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class SnsTracingTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.localstack()


  def cleanupSpec() {
    awsConnector.disconnect()
  }

  def "SNS notification triggers SQS message consumed with AWS SDK"() {
    setup:
    String queueName = "snsToSqsTestQueue"
    String topicName = "snsToSqsTestTopic"

    String queueUrl = awsConnector.createQueue(queueName)
    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn)

    when:
    awsConnector.publishSampleNotification(topicArn)
    awsConnector.receiveMessage(queueUrl)

    then:
    assertTraces(7) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateQueue"
            "aws.queue.name" queueName
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(1, 1) {

        span(0) {
          name "SQS.GetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(2, 1) {

        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(3, 1) {

        span(0) {
          name "SNS.CreateTopic"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateTopic"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(4, 1) {

        span(0) {
          name "SNS.Subscribe"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Subscribe"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(5, 2) {
        span(0) {
          name "SNS.Publish"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Publish"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(6, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
    }
  }
}