/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableList;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractAws2SqsTracingTest extends AbstractAws2SqsBaseTest {

  @Override
  protected void assertSqsTraces(boolean withParent, boolean captureHeaders) {
    int offset = withParent ? 2 : 0;
    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    getTesting()
        .waitAndAssertTraces(
            trace -> trace.hasSpansSatisfyingExactly(span -> createQueueSpan(span)),
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
                                      AWS_REQUEST_ID,
                                      val ->
                                          val.matches(
                                              "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "Sqs"),
                                  equalTo(RPC_METHOD, "SendMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                  equalTo(MESSAGING_OPERATION, "publish"),
                                  satisfies(
                                      MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class))));

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
                                        AWS_REQUEST_ID,
                                        val ->
                                            val.matches(
                                                "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                    equalTo(RPC_SYSTEM, "aws-api"),
                                    equalTo(RPC_SERVICE, "Sqs"),
                                    equalTo(RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(SERVER_ADDRESS, "localhost"),
                                    equalTo(SERVER_PORT, sqsPort))));
              }

              spanAsserts.addAll(
                  Arrays.asList(
                      span -> {
                        List<AttributeAssertion> attributes =
                            new ArrayList<>(
                                Arrays.asList(
                                    equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                    equalTo(RPC_SYSTEM, "aws-api"),
                                    equalTo(RPC_SERVICE, "Sqs"),
                                    equalTo(RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(SERVER_ADDRESS, "localhost"),
                                    equalTo(SERVER_PORT, sqsPort),
                                    equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                    equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                    equalTo(MESSAGING_OPERATION, "receive"),
                                    equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));

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
                                    equalTo(RPC_SYSTEM, "aws-api"),
                                    equalTo(RPC_SERVICE, "Sqs"),
                                    equalTo(RPC_METHOD, "ReceiveMessage"),
                                    equalTo(HTTP_REQUEST_METHOD, "POST"),
                                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                    satisfies(
                                        URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
                                    equalTo(SERVER_ADDRESS, "localhost"),
                                    equalTo(SERVER_PORT, sqsPort),
                                    equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                    equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                    equalTo(MESSAGING_OPERATION, "process"),
                                    satisfies(
                                        MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class))));

                        if (captureHeaders) {
                          attributes.add(
                              satisfies(
                                  stringArrayKey("messaging.header.test_message_header"),
                                  v -> v.isEqualTo(singletonList("test"))));
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
                  span -> publishSpan(span, queueUrl, "SendMessageBatch"));
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
                              equalTo(RPC_SYSTEM, "aws-api"),
                              equalTo(RPC_SERVICE, "Sqs"),
                              equalTo(RPC_METHOD, "ReceiveMessage"),
                              equalTo(HTTP_REQUEST_METHOD, "POST"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                              satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, sqsPort),
                              equalTo(MESSAGING_SYSTEM, AWS_SQS),
                              equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                              equalTo(MESSAGING_OPERATION, "receive"),
                              equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3)));

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
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "Sqs"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      satisfies(
                                          URL_FULL,
                                          v -> v.startsWith("http://localhost:" + sqsPort)),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                      equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                      equalTo(MESSAGING_OPERATION, "process"),
                                      satisfies(
                                          MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class)));
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
