/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.sdk.trace.data.SpanData
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

  abstract SqsClient configureSqsClient(SqsClient sqsClient)

  abstract SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient)

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

  void assertSqsTraces(withParent = false, captureHeaders = false) {
    assertTraces(3) {
      SpanData publishSpan
      trace(0, 1) {

        span(0) {
          name "Sqs.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.name" "testSdkSqs"
            "aws.requestId" { it == "00000000-0000-0000-0000-000000000000" || it == "UNKNOWN" }
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "rpc.method" "CreateQueue"
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
      trace(1, 1) {
        span(0) {
          name "testSdkSqs publish"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.requestId" { it == "00000000-0000-0000-0000-000000000000" || it == "UNKNOWN" }
            "rpc.system" "aws-api"
            "rpc.method" "SendMessage"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "publish"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            if (captureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        publishSpan = span(0)
      }
      def offset = withParent ? 2 : 0
      trace(2, 3 + offset) {
        if (withParent) {
          span(0) {
            name "parent"
            hasNoParent()
          }
          /**
           * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
           * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
           */
          span(1) {
            name "Sqs.ReceiveMessage"
            kind CLIENT
            childOf span(0)
            hasNoLinks()
            attributes {
              "aws.agent" "java-aws-sdk"
              "aws.requestId" { it == "00000000-0000-0000-0000-000000000000" || it == "UNKNOWN" }
              "rpc.method" "ReceiveMessage"
              "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
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
        span(0 + offset) {
          name "testSdkSqs receive"
          kind CONSUMER
          if (withParent) {
           childOf span(0)
          } else {
            hasNoParent()
          }
          hasNoLinks()
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
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            if (captureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(1 + offset) {
          name "testSdkSqs process"
          kind CONSUMER
          childOf span(0 + offset)
          hasLink(publishSpan)
          attributes {
            "aws.agent" "java-aws-sdk"
            "rpc.method" "ReceiveMessage"
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            if (captureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(2 + offset) {
          name "process child"
          childOf span(1 + offset)
          attributes {
          }
        }
      }
    }
  }

  def "simple sqs producer-consumer services: sync"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest)

    when:
    client.sendMessage(sendMessageRequest)

    def resp = client.receiveMessage(receiveMessageRequest)

    then:
    resp.messages.size() == 1
    resp.messages.each {message -> runWithSpan("process child") {}}
    assertSqsTraces()
  }

  def "capture message header as span attribute"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest)

    when:
    SendMessageRequest newSendMessageRequest = sendMessageRequest.toBuilder().messageAttributes(
      Collections.singletonMap("test-message-header",
      MessageAttributeValue.builder().dataType("String").stringValue("test").build())
    ).build()
    client.sendMessage(newSendMessageRequest)

    ReceiveMessageRequest newReceiveMessageRequest = receiveMessageRequest.toBuilder()
      .messageAttributeNames("test-message-header").build()
    def resp = client.receiveMessage(newReceiveMessageRequest)

    then:
    resp.messages.size() == 1
    // using forEach instead of each here to test different ways of iterating messages list
    resp.messages.forEach {message -> runWithSpan("process child") {}}
    assertSqsTraces(false, true)
  }

  def "simple sqs producer-consumer services with parent: sync"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest)

    when:
    client.sendMessage(sendMessageRequest)

    def resp = runWithSpan("parent") {
      client.receiveMessage(receiveMessageRequest)
    }

    then:
    resp.messages.size() == 1
    resp.messages.each {message -> runWithSpan("process child") {}}
    assertSqsTraces(true)
  }

  def "simple sqs producer-consumer services: async"() {
    setup:
    def builder = SqsAsyncClient.builder()
    configureSdkClient(builder)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest).get()

    when:
    client.sendMessage(sendMessageRequest).get()

    def resp = client.receiveMessage(receiveMessageRequest).get()

    then:
    resp.messages.size() == 1
    resp.messages.each {message -> runWithSpan("process child") {}}
    assertSqsTraces()
  }

  def "batch sqs producer-consumer services: sync"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest)

    when:
    client.sendMessageBatch(sendMessageBatchRequest)

    def resp = client.receiveMessage(receiveMessageBatchRequest)
    resp.messages.each {message -> runWithSpan("process child") {}}
    def totalAttrs = resp.messages.sum {it.messageAttributes().size() }

    then:
    resp.messages.size() == 3

    // +2: 3 messages, 2x traceparent, 1x not injected due to too many attrs
    totalAttrs == 18 + (sqsAttributeInjectionEnabled ? 2 : 0)

    assertTraces(3) {
      def publishSpan
      trace(0, 1) {

        span(0) {
          name "Sqs.CreateQueue"
          kind CLIENT
        }
      }
      trace(1, 1) {
        span(0) {
          name "testSdkSqs publish"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "aws.requestId" { it.trim() == "00000000-0000-0000-0000-000000000000" || it == "UNKNOWN" }
            "rpc.system" "aws-api"
            "rpc.method" "SendMessageBatch"
            "rpc.service" "Sqs"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" { it.startsWith("http://localhost:$sqsPort") }
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "publish"
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
        publishSpan = span(0)
      }
      trace(2, 1 + 2 * 3) {
        span(0) {
          name "testSdkSqs receive"
          kind CONSUMER
          hasNoParent()
          hasNoLinks()

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
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
          }
        }
        if (!xrayInjectionEnabled) {
          // one of the 3 process spans is expected to not have a span link, sort them so that the
          // last one is the one with missing link
          if (spans.get(1).links.empty) {
            spans.swap(1, 5)
            spans.swap(2, 6)
          } else if (spans.get(3).links.empty) {
            spans.swap(3, 5)
            spans.swap(4, 6)
          }
        }
        for (int i: 0..2) {
          span(1 + 2*i) {
            name "testSdkSqs process"
            kind CONSUMER
            childOf span(0)
            if (!xrayInjectionEnabled && i == 2) {
              // last message in batch has too many attributes so injecting tracing header is not
              // possible
              hasNoLinks()
            } else {
              hasLink(publishSpan)
            }
            attributes {
              "aws.agent" "java-aws-sdk"
              "rpc.method" "ReceiveMessage"
              "rpc.system" "aws-api"
              "rpc.service" "Sqs"
              "http.method" "POST"
              "http.url" { it.startsWith("http://localhost:$sqsPort") }
              "net.peer.name" "localhost"
              "net.peer.port" sqsPort
              "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
              "$SemanticAttributes.MESSAGING_OPERATION" "process"
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            }
          }
          span(1 + 2*i + 1) {
            name "process child"
            childOf span(1 + 2*i)
            attributes {
            }
          }
        }
      }
    }
  }
}
