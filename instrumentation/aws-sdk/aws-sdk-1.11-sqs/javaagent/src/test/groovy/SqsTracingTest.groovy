/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.api.trace.Span.Kind.CONSUMER
import static io.opentelemetry.api.trace.Span.Kind.PRODUCER

import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.opentelemetry.instrumentation.test.AgentTestRunner
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

class SqsTracingTest extends AgentTestRunner {

  @Shared
  LocalStackContainer sqs
  @Shared
  AmazonSQSAsyncClient client
  @Shared
  int sqsPort

  def setupSpec() {

    sqs = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
        .withServices(LocalStackContainer.Service.SQS)
    sqs.start()
    sqsPort = sqs.getMappedPort(4566)
    client = AmazonSQSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(sqs.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(sqs.getDefaultCredentialsProvider())
      .build()
  }

  def cleanupSpec() {
    if (sqs != null) {
      sqs.stop()
    }
  }

  def "simple sqs producer-consumer services"() {
    setup:
    client.createQueue("testSdkSqs")

    when:
    SendMessageRequest send = new SendMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs", "{\"type\": \"hello\"}")
    client.sendMessage(send)
    client.receiveMessage("http://localhost:$sqsPort/000000000000/testSdkSqs")

    then:
    assertTraces(2) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "CreateQueueRequest"
            "aws.queue.name" "testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 3) {

        span(0) {
          name "SQS.SendMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "SendMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
        span(1) {
          name "SQS.SendMessage"
          kind PRODUCER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "SendMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
          }
        }
        span(2) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(1)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
          }
        }
      }
    }
  }
}
