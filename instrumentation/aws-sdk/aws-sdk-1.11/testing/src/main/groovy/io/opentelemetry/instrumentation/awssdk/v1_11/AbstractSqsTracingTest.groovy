/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class AbstractSqsTracingTest extends InstrumentationSpecification {

  abstract AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client)

  @Shared
  def sqs
  @Shared
  AmazonSQSAsyncClient client
  @Shared
  int sqsPort

  def setupSpec() {

    sqsPort = PortUtils.findOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

    def credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"))
    def endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq")
    client = configureClient(AmazonSQSAsyncClient.asyncBuilder()).withCredentials(credentials).withEndpointConfiguration(endpointConfiguration).build()
  }

  def cleanupSpec() {
    if (sqs != null) {
      sqs.stopAndWait()
    }
  }

  boolean hasHttpClientSpan() {
    false
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
            "aws.queue.name" "testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "CreateQueue"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" IP_TCP
          }
        }
      }
      trace(1, hasHttpClientSpan() ? 3 : 2) {
        span(0) {
          name "SQS.SendMessage"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.method" "SendMessage"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" IP_TCP
          }
        }
        def offset = 0
        if (hasHttpClientSpan()) {
          offset = 1
          span(1) {
            name "HTTP POST"
            kind CLIENT
            childOf span(0)
          }
        }
        span(1 + offset) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "http.user_agent" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" IP_TCP
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
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" IP_TCP
          }
        }
      }
    }
  }

  def "only adds attribute name once when request reused"() {
    setup:
    client.createQueue("testSdkSqs2")

    when:
    SendMessageRequest send = new SendMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs2", "{\"type\": \"hello\"}")
    client.sendMessage(send)
    ReceiveMessageRequest receive = new ReceiveMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs2")
    client.receiveMessage(receive)
    client.sendMessage(send)
    client.receiveMessage(receive)

    then:
    receive.getAttributeNames() == ["AWSTraceHeader"]
  }
}