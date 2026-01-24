/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public abstract class AbstractAws2SqsSuppressReceiveSpansTest extends AbstractAws2SqsBaseTest {

  @Override
  protected void assertSqsTraces(boolean withParent, boolean captureHeaders) {
    List<Consumer<TraceAssert>> traceAsserts =
        new ArrayList<>(
            Arrays.asList(
                trace -> trace.hasSpansSatisfyingExactly(span -> createQueueSpan(span)),
                trace ->
                    trace.hasSpansSatisfyingExactly(
                        span -> publishSpan(span, queueUrl, "SendMessage"),
                        span -> processSpan(span, trace.getSpan(0)),
                        span ->
                            span.hasName("process child")
                                .hasParent(trace.getSpan(1))
                                .hasAttributes(Attributes.empty()))));

    if (withParent) {
      /*
       * This span represents HTTP "sending of receive message" operation. It's always single,
       * while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then
       * HTTP instrumentation span would appear)
       */
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> {
                    List<AttributeAssertion> attrs = new ArrayList<>();
                    attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                    attrs.add(equalTo(AWS_SQS_QUEUE_URL, queueUrl));
                    attrs.add(
                        satisfies(
                            AWS_REQUEST_ID,
                            val ->
                                val.matches(
                                    "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")));
                    attrs.add(rpcSystemAssertion("aws-api"));
                    attrs.addAll(rpcMethodAssertions("Sqs", "ReceiveMessage"));
                    attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                    attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                    attrs.add(
                        satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)));
                    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                    attrs.add(equalTo(SERVER_PORT, sqsPort));
                    span.hasName("Sqs.ReceiveMessage")
                        .hasKind(SpanKind.CLIENT)
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(attrs);
                  }));
    }

    getTesting().waitAndAssertTraces(traceAsserts);
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void testBatchSqsProducerConsumerServicesSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessageBatch(sendMessageBatchRequest);

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);

    int totalAttrs =
        response.messages().stream().mapToInt(message -> message.messageAttributes().size()).sum();

    // generates the process spans
    response.messages().forEach(message -> {});

    assertThat(response.messages().size()).isEqualTo(3);

    // +2: 3 messages, 2x traceparent, 1x not injected due to too many attrs
    assertThat(totalAttrs).isEqualTo(18 + (isSqsAttributeInjectionEnabled() ? 2 : 0));

    List<Consumer<TraceAssert>> traceAsserts =
        new ArrayList<>(
            Arrays.asList(
                trace -> trace.hasSpansSatisfyingExactly(span -> createQueueSpan(span)),
                trace -> {
                  List<Consumer<SpanDataAssert>> spanAsserts =
                      new ArrayList<>(
                          singletonList(span -> publishSpan(span, queueUrl, "SendMessageBatch")));

                  for (int i = 0; i <= (isXrayInjectionEnabled() ? 2 : 1); i++) {
                    spanAsserts.add(span -> processSpan(span, trace.getSpan(0)));
                  }
                  trace.hasSpansSatisfyingExactly(spanAsserts);
                }));

    if (!isXrayInjectionEnabled()) {
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    List<AttributeAssertion> attrs = new ArrayList<>();
                    attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                    attrs.add(rpcSystemAssertion("aws-api"));
                    attrs.addAll(rpcMethodAssertions("Sqs", "ReceiveMessage"));
                    attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                    attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                    attrs.add(
                        satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)));
                    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                    attrs.add(equalTo(SERVER_PORT, sqsPort));
                    attrs.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                    attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                    attrs.add(equalTo(MESSAGING_OPERATION, "process"));
                    attrs.add(satisfies(MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class)));
                    span.hasName("testSdkSqs process")
                        .hasKind(SpanKind.CONSUMER)
                        // TODO: This is not good, and can also happen if producer is not
                        // instrumented
                        .hasNoParent()
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(attrs);
                  }));
    }
    getTesting().waitAndAssertTraces(traceAsserts);
  }
}
