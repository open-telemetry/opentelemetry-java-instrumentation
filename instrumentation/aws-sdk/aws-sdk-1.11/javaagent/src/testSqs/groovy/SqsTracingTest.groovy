/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import spock.lang.Shared

class SqsTracingTest extends AgentInstrumentationSpecification {

  @Shared
  def sqs
  @Shared
  AmazonSQSAsyncClient client
  @Shared
  int sqsPort

  def setupSpec() {

    sqsPort = PortUtils.randomOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

    def credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"))
    def endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:"+sqsPort, "elasticmq")
    client = AmazonSQSAsyncClient.asyncBuilder().withCredentials(credentials).withEndpointConfiguration(endpointConfiguration).build()
  }

  def cleanupSpec() {
    if (sqs != null) {
      sqs.stopAndWait()
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
    assertTraces(3) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "CreateQueue"
            "aws.queue.name" "testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 2) {
        span(0) {
          name "SQS.SendMessage"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "SendMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "http.user_agent" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(2, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
    }
  }
}