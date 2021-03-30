/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Ignore
import spock.lang.Shared

@Ignore("Does not work with localstack - X-Ray features needed")
class SnsCamelTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.liveAws()

  def "AWS SDK SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SnsConfig, ImmutableMap.of("topicName", topicName, "queueName", queueName))

    // setup infra
    String queueUrl = awsConnector.createQueue(queueName)
    String queueArn = awsConnector.getQueueArn(queueName)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn)

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl)

    // wait for setup traces
    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    awsConnector.publishSampleNotification(topicArn)

    then:
    assertTraces(4) {
      trace(0, 3) {
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(2) {
          name "snsCamelTest"
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient"
            "messaging.destination" queueName
            "messaging.message_id" String
          }
        }
      }
      // http client span
      trace(1, 1) {
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      // camel polling
      trace(3, 1) {
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
    }
    cleanup:
    awsConnector.purgeQueue(queueUrl)
    camelApp.stop()
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
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
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
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      // test message
      trace(5, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
    }
    clearExportedData()
  }


  def "camel SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, SnsConfig, ImmutableMap.of("topicName", topicName, "queueName", queueName))

    // setup infra
    String queueUrl = awsConnector.createQueue(queueName)
    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    awsConnector.createTopicAndSubscribeQueue(topicName, queueArn)

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl)

    // wait for setup traces
    waitAndClearSetupTraces(queueUrl, queueName)

    when:
    camelApp.start()
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(4) {
      trace(0, 5) {

        span(0) {
          name "input"
          kind INTERNAL
          hasNoParent()
          attributes {
            "apache-camel.uri" "direct://input"
          }
        }
        span(1) {
          name topicName
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sns://${topicName}?amazonSNSClient=%23snsClient"
            "messaging.destination" topicName
          }
        }
        span(2) {
          name "SNS.Publish"
          kind CLIENT
          childOf span(1)
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(3) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(2)
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(4) {
          name "snsCamelTest"
          kind INTERNAL
          childOf span(2)
          attributes {
            "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient"
            "messaging.destination" queueName
            "messaging.message_id" String
          }
        }
      }
      trace(1, 1) {
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      // camel polling
      trace(3, 1) {
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
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
    }
    cleanup:
    awsConnector.purgeQueue(queueUrl)
    camelApp.stop()
  }
}

