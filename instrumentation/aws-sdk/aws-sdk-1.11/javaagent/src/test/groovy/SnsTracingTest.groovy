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
    def receiveMessageResult = awsConnector.receiveMessage(queueUrl)
    receiveMessageResult.messages.each {message ->
      runWithSpan("process child") {}
    }

    then:
    assertTraces(6) {
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topicArn
          }
        }
      }
      trace(5, 3) {
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
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topicArn
          }
        }
        span(1) {
          name "snsToSqsTestQueue process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "ReceiveMessage"
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "POST"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_FULL" String
            "$SemanticAttributes.SERVER_ADDRESS" String
            "$SemanticAttributes.SERVER_PORT" { it == null || Number }
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "snsToSqsTestQueue"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
          }
        }
        span(2) {
          name "process child"
          childOf span(1)
          attributes {
          }
        }
      }
    }
  }
}
