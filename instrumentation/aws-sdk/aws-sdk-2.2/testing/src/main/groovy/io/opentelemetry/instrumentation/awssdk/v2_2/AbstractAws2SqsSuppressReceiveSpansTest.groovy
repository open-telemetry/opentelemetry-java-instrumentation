/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
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

abstract class AbstractAws2SqsSuppressReceiveSpansTest extends InstrumentationSpecification {

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

  void assertSqsTraces(withParent = false) {
    assertTraces(2 + (withParent ? 1 : 0)) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
            "$ServerAttributes.SERVER_ADDRESS" "localhost"
            "$ServerAttributes.SERVER_PORT" sqsPort
          }
        }
      }
      trace(1, 3) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
            "$ServerAttributes.SERVER_ADDRESS" "localhost"
            "$ServerAttributes.SERVER_PORT" sqsPort
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
          }
        }
        span(1) {
          name "testSdkSqs process"
          kind CONSUMER
          childOf span(0)
          hasNoLinks()
          attributes {
            "aws.agent" "java-aws-sdk"
            "rpc.method" "ReceiveMessage"
            "rpc.system" "aws-api"
            "rpc.service" "Sqs"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
            "$ServerAttributes.SERVER_ADDRESS" "localhost"
            "$ServerAttributes.SERVER_PORT" sqsPort
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
          }
        }
        span(2) {
          name "process child"
          childOf span(1)
          attributes {
          }
        }
      }
      if (withParent) {
        /**
         * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
         * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
         */
        trace(2, 2) {
          span(0) {
            name "parent"
            hasNoParent()
          }
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
              "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
              "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
              "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
              "$ServerAttributes.SERVER_ADDRESS" "localhost"
              "$ServerAttributes.SERVER_PORT" sqsPort
            }
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
    def totalAttrs = resp.messages().sum {it.messageAttributes().size() }

    then:
    resp.messages().size() == 3

    // +2: 3 messages, 2x traceparent, 1x not injected due to too many attrs
    totalAttrs == 18 + (sqsAttributeInjectionEnabled ? 2 : 0)

    assertTraces(xrayInjectionEnabled ? 2 : 3) {
      trace(0, 1) {

        span(0) {
          name "Sqs.CreateQueue"
          kind CLIENT
        }
      }
      trace(1, xrayInjectionEnabled ? 4 : 3) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
            "$ServerAttributes.SERVER_ADDRESS" "localhost"
            "$ServerAttributes.SERVER_PORT" sqsPort
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
          }
        }
        for (int i: 1..(xrayInjectionEnabled ? 3 : 2)) {
          span(i) {
            name "testSdkSqs process"
            kind CONSUMER
            childOf span(0)
            hasNoLinks()

            attributes {
              "aws.agent" "java-aws-sdk"
              "rpc.method" "ReceiveMessage"
              "rpc.system" "aws-api"
              "rpc.service" "Sqs"
              "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
              "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
              "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
              "$ServerAttributes.SERVER_ADDRESS" "localhost"
              "$ServerAttributes.SERVER_PORT" sqsPort
              "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
              "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
              "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
            }
          }
        }
      }
      if (!xrayInjectionEnabled) {
        trace(2, 1) {
          span(0) {
            name "testSdkSqs process"
            kind CONSUMER

            // TODO This is not nice at all, and can also happen if producer is not instrumented
            hasNoParent()
            hasNoLinks()

            attributes {
              "aws.agent" "java-aws-sdk"
              "rpc.method" "ReceiveMessage"
              "rpc.system" "aws-api"
              "rpc.service" "Sqs"
              "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
              "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
              "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:$sqsPort") }
              "$ServerAttributes.SERVER_ADDRESS" "localhost"
              "$ServerAttributes.SERVER_PORT" sqsPort
              "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
              "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
              "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
            }
          }
        }
      }
    }
  }
}
