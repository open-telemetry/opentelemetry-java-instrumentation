/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.SemanticAttributes
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER

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
            "rpc.method" "CreateQueue"
            "aws.queue.name" queueName
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "rpc.method" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "rpc.method" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "rpc.method" "CreateTopic"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "rpc.method" "Subscribe"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "rpc.method" "Publish"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "ReceiveMessage"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
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
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "ReceiveMessage"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "net.peer.port" { it == null || Number }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
      }
    }
  }
}
