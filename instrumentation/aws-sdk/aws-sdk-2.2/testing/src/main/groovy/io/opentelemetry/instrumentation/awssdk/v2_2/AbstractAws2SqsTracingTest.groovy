/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.SemanticAttributes
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsBaseClientBuilder
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
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

  static Map<String, MessageAttributeValue> dummyMessageAttributes(count) {
    (0..<count).collectEntries {
      [
          "a$it".toString(),
          MessageAttributeValue.builder().stringValue("v$it").dataType("String").build()]
    }
  }

  String queueUrl = "http://localhost:$sqsPort/000000000000/testSdkSqs"

  ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
      .queueUrl(queueUrl)
      .build()

  ReceiveMessageRequest receiveMessageBatchRequest = ReceiveMessageRequest.builder()
      .queueUrl(queueUrl)
      .maxNumberOfMessages(3)
      .messageAttributeNames("All")
      .waitTimeSeconds(5)
      .build()

  CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
      .queueName("testSdkSqs")
      .build()

  SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody("{\"type\": \"hello\"}")
      .build()

  SendMessageBatchRequest sendMessageBatchRequest = SendMessageBatchRequest.builder()
      .queueUrl(queueUrl)
      .entries(
          e -> e.messageBody("e1").id("i1"),
          // 8 attributes, injection always possible
      e -> e.messageBody("e2").id("i2")
          .messageAttributes(dummyMessageAttributes(8)),
          // 10 attributes, injection with custom propagator never possible
      e -> e.messageBody("e3").id("i3").messageAttributes(dummyMessageAttributes(10)))
      .build()

  boolean isSqsAttributeInjectionEnabled() {
    AbstractAws2ClientCoreTest.isSqsAttributeInjectionEnabled()
  }

  boolean isXrayInjectionEnabled() {
    true
  }

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
    sqs = SQSRestServerBuilder.withPort(0).withInterface("localhost").start()
    def server = sqs.waitUntilStarted()
    sqsPort = server.localAddress().port
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
          hasNoLinks() // TODO: Link to receive operation?
          attributes {
            "aws.agent" "java-aws-sdk"
            "rpc.method" "ReceiveMessage"
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
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(2, 1) {
        span(0) {
          name "Sqs.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          hasNoLinks()
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

    def resp = client.receiveMessage(receiveMessageRequest)

    then:
    resp.messages().size() == 1
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

    def resp = client.receiveMessage(receiveMessageRequest).get()

    then:
    resp.messages().size() == 1
    assertSqsTraces()
  }

  def "batch sqs producer-consumer services: sync"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = builder.build()

    client.createQueue(createQueueRequest)

    when:
    client.sendMessageBatch(sendMessageBatchRequest)

    def resp = client.receiveMessage(receiveMessageBatchRequest)
    def totalAttrs = resp.messages().sum {it.messageAttributes().size() }

    then:
    resp.messages().size() == 3

    // +2: 3 messages, 2x traceparent, 1x not injected due to too many attrs
    totalAttrs == 18 + (sqsAttributeInjectionEnabled ? 2 : 0)

    assertTraces(xrayInjectionEnabled ? 3 : 4) {
      trace(0, 1) {

        span(0) {
          name "Sqs.CreateQueue"
          kind CLIENT
        }
      }
      trace(1, xrayInjectionEnabled ? 4 : 3) {
        span(0) {
          name "Sqs.SendMessageBatch"
          kind CLIENT // TODO: Probably this should be producer, but that would be a breaking change
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.requestId" "00000000-0000-0000-0000-000000000000"
            "rpc.system" "aws-api"
            "rpc.method" "SendMessageBatch"
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
        for (int i: 1..(xrayInjectionEnabled ? 3 : 2)) {
          span(i) {
            name "Sqs.ReceiveMessage"
            kind CONSUMER
            childOf span(0)
            hasNoLinks() // TODO: Link to receive operation?

            attributes {
              "aws.agent" "java-aws-sdk"
              "rpc.method" "ReceiveMessage"
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
      if (!xrayInjectionEnabled) {
        trace(3, 1) {
          span(0) {
            name "Sqs.ReceiveMessage"
            kind CONSUMER

            // TODO This is not nice at all, and can also happen if producer is not instrumented
            hasNoParent()
            hasNoLinks() // TODO: Link to receive operation?

            attributes {
              "aws.agent" "java-aws-sdk"
              "rpc.method" "ReceiveMessage"
              "rpc.system" "aws-api"
              "rpc.service" "Sqs"
              "http.method" "POST"
              "http.status_code" 200
              "http.url" { it.startsWith("http://localhost:$sqsPort") }
              "net.peer.name" "localhost"
              "$SemanticAttributes.USER_AGENT_ORIGINAL" String
              "net.peer.port" sqsPort
              "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
              "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            }
          }
        }
      }
    }
  }
}
