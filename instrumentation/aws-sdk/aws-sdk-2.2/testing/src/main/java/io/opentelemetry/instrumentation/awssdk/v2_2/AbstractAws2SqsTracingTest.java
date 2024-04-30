/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.pekko.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public abstract class AbstractAws2SqsTracingTest {

  protected abstract InstrumentationExtension getTesting();

  protected abstract SqsClient configureSqsClient(SqsClient sqsClient);

  protected abstract SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient);

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  private static int sqsPort;
  private static SQSRestServer sqs;

  static Map<String, MessageAttributeValue> dummyMessageAttributes(int count) {
    Map<String, MessageAttributeValue> map = new HashMap<>();
    for (int i = 0; i < count; i++) {
      map.put(
          "a" + i, MessageAttributeValue.builder().stringValue("v" + i).dataType("String").build());
    }
    return map;
  }

  private final String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";

  ReceiveMessageRequest receiveMessageRequest =
      ReceiveMessageRequest.builder().queueUrl(queueUrl).build();

  ReceiveMessageRequest receiveMessageBatchRequest =
      ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(3)
          .messageAttributeNames("All")
          .waitTimeSeconds(5)
          .build();

  CreateQueueRequest createQueueRequest =
      CreateQueueRequest.builder().queueName("testSdkSqs").build();

  SendMessageRequest sendMessageRequest =
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{\"type\": \"hello\"}").build();

  @SuppressWarnings("unchecked")
  SendMessageBatchRequest sendMessageBatchRequest =
      SendMessageBatchRequest.builder()
          .queueUrl(queueUrl)
          .entries(
              e -> e.messageBody("e1").id("i1"),
              // 8 attributes, injection always possible
              e -> e.messageBody("e2").id("i2").messageAttributes(dummyMessageAttributes(8)),
              // 10 attributes, injection with custom propagator never possible
              e -> e.messageBody("e3").id("i3").messageAttributes(dummyMessageAttributes(10)))
          .build();

  boolean isSqsAttributeInjectionEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  boolean isXrayInjectionEnabled() {
    return true;
  }

  void configureSdkClient(SqsClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  void configureSdkClient(SqsAsyncClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  @BeforeAll
  static void setUp() {
    sqs = SQSRestServerBuilder.withPort(0).withInterface("localhost").start();
    Http.ServerBinding server = sqs.waitUntilStarted();
    sqsPort = server.localAddress().getPort();
  }

  @AfterAll
  static void cleanUp() {
    if (sqs != null) {
      sqs.stopAndWait();
    }
  }

  void assertSqsTraces(Boolean withParent, Boolean captureHeaders) {
    int offset = withParent ? 2 : 0;
    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("Sqs.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
                                satisfies(
                                    stringKey("aws.requestId"),
                                    val ->
                                        val.satisfiesAnyOf(
                                            v ->
                                                assertThat(v)
                                                    .isEqualTo(
                                                        "00000000-0000-0000-0000-000000000000"),
                                            v -> assertThat(v).isEqualTo("UNKNOWN"))),
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "CreateQueue"),
                                equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                satisfies(
                                    UrlAttributes.URL_FULL,
                                    v -> v.startsWith("http://localhost:" + sqsPort)),
                                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                equalTo(ServerAttributes.SERVER_PORT, sqsPort))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      publishSpan.set(trace.getSpan(0));
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              Arrays.asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(
                                      stringKey("aws.queue.url"),
                                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                  satisfies(
                                      stringKey("aws.requestId"),
                                      val ->
                                          val.satisfiesAnyOf(
                                              v ->
                                                  assertThat(v)
                                                      .isEqualTo(
                                                          "00000000-0000-0000-0000-000000000000"),
                                              v -> assertThat(v).isEqualTo("UNKNOWN"))),
                                  equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                  equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                  equalTo(RpcIncubatingAttributes.RPC_METHOD, "SendMessage"),
                                  equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      UrlAttributes.URL_FULL,
                                      v -> v.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                  equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                                  equalTo(
                                      MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                                  equalTo(
                                      MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                      "testSdkSqs"),
                                  equalTo(
                                      MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                                  satisfies(
                                      MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                      v -> v.isInstanceOf(String.class))));

                      if (captureHeaders) {
                        attributes.add(
                            satisfies(
                                stringArrayKey("messaging.header.test_message_header"),
                                v -> v.isEqualTo(ImmutableList.of("test"))));
                      }
                      span.hasName("testSdkSqs publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }),
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              if (withParent) {
                spanAsserts.addAll(
                    Arrays.asList(
                        span -> span.hasName("parent").hasNoParent(),
                        /*
                         * This span represents HTTP "sending of receive message" operation. It's always single,
                         * while there can be multiple CONSUMER spans (one per consumed message).
                         * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then
                         * HTTP instrumentation span would appear)
                         */
                        span ->
                            span.hasName("Sqs.ReceiveMessage")
                                .hasKind(SpanKind.CLIENT)
                                .hasParent(trace.getSpan(0))
                                .hasTotalRecordedLinks(0)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                    equalTo(
                                        stringKey("aws.queue.url"),
                                        "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                    satisfies(
                                        stringKey("aws.requestId"),
                                        val ->
                                            val.satisfiesAnyOf(
                                                v ->
                                                    assertThat(v)
                                                        .isEqualTo(
                                                            "00000000-0000-0000-0000-000000000000"),
                                                v -> assertThat(v).isEqualTo("UNKNOWN"))),
                                    equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                    equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                    equalTo(RpcIncubatingAttributes.RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        UrlAttributes.URL_FULL,
                                        v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                    equalTo(ServerAttributes.SERVER_PORT, sqsPort))));
              }

              spanAsserts.addAll(
                  Arrays.asList(
                      span -> {
                        List<AttributeAssertion> attributes =
                            new ArrayList<>(
                                Arrays.asList(
                                    equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                    equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                    equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                    equalTo(RpcIncubatingAttributes.RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        UrlAttributes.URL_FULL,
                                        v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                    equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                        "AmazonSQS"),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                        "testSdkSqs"),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                        "receive"),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT,
                                        1)));

                        if (captureHeaders) {
                          attributes.add(
                              satisfies(
                                  stringArrayKey("messaging.header.test_message_header"),
                                  v -> v.isEqualTo(ImmutableList.of("test"))));
                        }

                        if (withParent) {
                          span.hasParent(trace.getSpan(0));
                        } else {
                          span.hasNoParent();
                        }

                        span.hasName("testSdkSqs receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasTotalRecordedLinks(0)
                            .hasAttributesSatisfyingExactly(attributes);
                      },
                      span -> {
                        List<AttributeAssertion> attributes =
                            new ArrayList<>(
                                Arrays.asList(
                                    equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                    equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                    equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                    equalTo(RpcIncubatingAttributes.RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        UrlAttributes.URL_FULL,
                                        v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                    equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                        "AmazonSQS"),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                        "testSdkSqs"),
                                    equalTo(
                                        MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                        "process"),
                                    satisfies(
                                        MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                        v -> v.isInstanceOf(String.class))));

                        if (captureHeaders) {
                          attributes.add(
                              satisfies(
                                  stringArrayKey("messaging.header.test_message_header"),
                                  v -> v.isEqualTo(ImmutableList.of("test"))));
                        }

                        span.hasName("testSdkSqs process")
                            .hasParent(trace.getSpan(0 + offset))
                            .hasKind(SpanKind.CONSUMER)
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link ->
                                                assertThat(link.getSpanContext().getSpanId())
                                                    .isEqualTo(publishSpan.get().getSpanId())))
                            .hasAttributesSatisfyingExactly(attributes);
                      },
                      span ->
                          span.hasName("process child")
                              .hasParent(trace.getSpan(1 + offset))
                              .hasAttributes(Attributes.empty())));
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  @Test
  void testSimpleSqsProducerConsumerServicesSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);

    client.sendMessage(sendMessageRequest);

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  @Test
  void testCaptureMessageHeaderAsAttributeSpan() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);

    SendMessageRequest newSendMessageRequest =
        sendMessageRequest.toBuilder()
            .messageAttributes(
                Collections.singletonMap(
                    "test-message-header",
                    MessageAttributeValue.builder().dataType("String").stringValue("test").build()))
            .build();
    client.sendMessage(newSendMessageRequest);

    ReceiveMessageRequest newReceiveMessageRequest =
        receiveMessageRequest.toBuilder().messageAttributeNames("test-message-header").build();
    ReceiveMessageResponse response = client.receiveMessage(newReceiveMessageRequest);

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, true);
  }

  @Test
  void testSimpleSqsProducerConsumerServicesWithParentSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);

    ReceiveMessageResponse response =
        getTesting().runWithSpan("parent", () -> client.receiveMessage(receiveMessageRequest));

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(true, false);
  }

  @SuppressWarnings("InterruptedExceptionSwallowed")
  @Test
  void testSimpleSqsProducerConsumerServicesAsync() throws Exception {
    SqsAsyncClientBuilder builder = SqsAsyncClient.builder();
    configureSdkClient(builder);
    SqsAsyncClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest).get();
    client.sendMessage(sendMessageRequest).get();

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest).get();

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  @Test
  void testBatchSqsProducerConsumerServicesSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessageBatch(sendMessageBatchRequest);

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));

    int totalAttrs =
        response.messages().stream().mapToInt(message -> message.messageAttributes().size()).sum();

    assertThat(response.messages().size()).isEqualTo(3);

    // +2: 3 messages, 2x traceparent, 1x not injected due to too many attrs
    assertThat(totalAttrs).isEqualTo(18 + (isSqsAttributeInjectionEnabled() ? 2 : 0));

    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace -> {
              publishSpan.set(trace.getSpan(0));
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("testSdkSqs publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                              equalTo(
                                  stringKey("aws.queue.url"),
                                  "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                              satisfies(
                                  stringKey("aws.requestId"),
                                  val ->
                                      val.satisfiesAnyOf(
                                          v ->
                                              assertThat(v.trim())
                                                  .isEqualTo(
                                                      "00000000-0000-0000-0000-000000000000"),
                                          v -> assertThat(v.trim()).isEqualTo("UNKNOWN"))),
                              equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                              equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                              equalTo(RpcIncubatingAttributes.RPC_METHOD, "SendMessageBatch"),
                              equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                              equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                              satisfies(
                                  UrlAttributes.URL_FULL,
                                  v -> v.startsWith("http://localhost:" + sqsPort)),
                              equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                              equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                              equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                              equalTo(
                                  MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                  "testSdkSqs"),
                              equalTo(
                                  MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish")));
            },
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(
                  span ->
                      span.hasName("testSdkSqs receive")
                          .hasKind(SpanKind.CONSUMER)
                          .hasNoParent()
                          .hasTotalRecordedLinks(0)
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                              equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                              equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                              equalTo(RpcIncubatingAttributes.RPC_METHOD, "ReceiveMessage"),
                              equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                              equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                              satisfies(
                                  UrlAttributes.URL_FULL,
                                  v -> v.startsWith("http://localhost:" + sqsPort)),
                              equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                              equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                              equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                              equalTo(
                                  MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                  "testSdkSqs"),
                              equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                              equalTo(
                                  MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, 3)));

              // one of the 3 process spans is expected to not have a span link
              for (int i = 0; i <= 2; i++) {
                int finalI = i;
                spanAsserts.addAll(
                    new ArrayList<>(
                        Arrays.asList(
                            span -> {
                              if (!isXrayInjectionEnabled() && finalI == 2) {
                                // last message in batch has too many attributes so injecting
                                // tracing header is not possible
                                span.hasTotalRecordedLinks(0);
                              } else {
                                span.hasLinksSatisfying(
                                    links ->
                                        assertThat(links)
                                            .singleElement()
                                            .satisfies(
                                                link ->
                                                    assertThat(link.getSpanContext().getSpanId())
                                                        .isEqualTo(publishSpan.get().getSpanId())));
                              }

                              span.hasName("testSdkSqs process")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
                                      equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Sqs"),
                                      equalTo(RpcIncubatingAttributes.RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                      satisfies(
                                          UrlAttributes.URL_FULL,
                                          v -> v.startsWith("http://localhost:" + sqsPort)),
                                      equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                      equalTo(ServerAttributes.SERVER_PORT, sqsPort),
                                      equalTo(
                                          MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                          "AmazonSQS"),
                                      equalTo(
                                          MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                          "testSdkSqs"),
                                      equalTo(
                                          MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                          "process"),
                                      satisfies(
                                          MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                          v -> v.isInstanceOf(String.class)));
                            },
                            span ->
                                span.hasName("process child")
                                    .hasParent(trace.getSpan(1 + 2 * finalI))
                                    .hasAttributes(Attributes.empty()))));
              }

              trace.hasSpansSatisfyingExactlyInAnyOrder(spanAsserts);
            });
  }
}
