/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.builder.SdkClientBuilder
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsBaseClientBuilder
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

abstract class AbstractAws2SqsTracingTest extends InstrumentationSpecification {

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER = StaticCredentialsProvider
    .create(AwsBasicCredentials.create("my-access-key", "my-secret-key"))

  @Shared
  def sqs

  @Shared
  int sqsPort

  ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
      .queueUrl("http://localhost:$sqsPort/000000000000/testSdkSqs")
      .build()

  CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
      .queueName("testSdkSqs")
      .build()

  SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
      .queueUrl("http://localhost:$sqsPort/000000000000/testSdkSqs")
      .messageBody("{\"type\": \"hello\"}")
      .build()

  void configureSdkClient(SqsBaseClientBuilder builder) {
    builder
      .overrideConfiguration(createOverrideConfigurationBuilder().build())
      .endpointOverride(new URI("http://localhost:" + sqsPort))
    builder
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
  }

  abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder()

  def setupSpec() {
    sqsPort = PortUtils.findOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

  }

  def cleanupSpec() {
    if (sqs != null) {
      sqs.stopAndWait()
    }
  }

  void assertSqsTraces() {
    assertTraces(3) {
      trace(0, 1) {

        span(0) {
          name "Sqs.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.name" "testSdkSqs"
            "aws.requestId" "00000000-0000-0000-0000-000000000000"
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "rpc.method" "CreateQueue"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
      }
      trace(1, 2) {
        span(0) {
          name "Sqs.SendMessage"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.requestId" "00000000-0000-0000-0000-000000000000"
            "rpc.system" "aws-api"
            "rpc.method" "SendMessage"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
        span(1) {
          name "Sqs.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "rpc.method" "ReceiveMessage"
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(2, 1) {
        span(0) {
          name "Sqs.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "00000000-0000-0000-0000-000000000000"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
      }
    }
  }

  def "simple sqs producer-consumer services: sync"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = builder.build()

    client.createQueue(createQueueRequest)

    when:
    client.sendMessage(sendMessageRequest)

    client.receiveMessage(receiveMessageRequest)

    then:
    assertSqsTraces()
  }

  def "simple sqs producer-consumer services: async"() {
    setup:
    def builder = SqsAsyncClient.builder()
    configureSdkClient(builder)
    def client = builder.build()

    client.createQueue(createQueueRequest).get()

    when:
    client.sendMessage(sendMessageRequest).get()

    client.receiveMessage(receiveMessageRequest).get()

    then:
    assertSqsTraces()
  }
}
