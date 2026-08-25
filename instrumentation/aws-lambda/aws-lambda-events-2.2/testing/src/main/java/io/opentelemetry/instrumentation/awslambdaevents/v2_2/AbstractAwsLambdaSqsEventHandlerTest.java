/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaSqsMetricsAssertions.assertMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_INVOCATION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.lang.reflect.Constructor;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
@ExtendWith(MockitoExtension.class)
public abstract class AbstractAwsLambdaSqsEventHandlerTest {

  private static final String AWS_TRACE_HEADER =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";
  private static final String AWS_TRACE_HEADER2 =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad9;Sampled=1";
  private static final String TRACE_ID = "5759e988bd862e3fe1be46a994272793";

  protected abstract RequestHandler<SQSEvent, ?> handler();

  protected abstract InstrumentationExtension testing();

  protected abstract String instrumentationName();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing().forceFlushCalled()).isTrue();
  }

  @Test
  void sameSource() {
    SQSEvent.SQSMessage message1 = newMessage();
    message1.setAttributes(singletonMap("AWSTraceHeader", AWS_TRACE_HEADER));
    message1.setMessageId("message1");
    message1.setEventSource("aws:sqs");
    message1.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setAttributes(emptyMap());
    message2.setMessageId("message2");
    message2.setEventSource("aws:sqs");
    message2.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");

    SQSEvent event = new SQSEvent();
    event.setRecords(asList(message1, message2));

    handler().handleRequest(event, context);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FAAS_INVOCATION_ID, "1-22-333")),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv() ? "process queue1" : "aws:sqs process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParentSpanId(trace.getSpan(0).getSpanId())
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(
                                    MESSAGING_OPERATION,
                                    emitOldMessagingSemconv() ? "process" : null),
                                equalTo(
                                    MESSAGING_DESTINATION_NAME,
                                    emitStableMessagingSemconv() ? "queue1" : null),
                                equalTo(
                                    MESSAGING_OPERATION_NAME,
                                    emitStableMessagingSemconv() ? "process" : null),
                                equalTo(
                                    MESSAGING_OPERATION_TYPE,
                                    emitStableMessagingSemconv() ? "process" : null),
                                equalTo(
                                    MESSAGING_BATCH_MESSAGE_COUNT,
                                    emitStableMessagingSemconv() ? Long.valueOf(2) : null))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .satisfiesExactly(
                                            link(
                                                "53995c3f42cd8ad8",
                                                emitStableMessagingSemconv()
                                                    ? Attributes.of(
                                                        MESSAGING_MESSAGE_ID, "message1")
                                                    : Attributes.empty())))));
    assertMetrics(testing(), instrumentationName(), "queue1", 1, 2, null);
  }

  @Test
  void differentSource() {
    SQSEvent.SQSMessage message1 = newMessage();
    message1.setAttributes(singletonMap("AWSTraceHeader", AWS_TRACE_HEADER));
    message1.setMessageId("message1");
    message1.setEventSource("aws:sqs");
    message1.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setAttributes(singletonMap("AWSTraceHeader", AWS_TRACE_HEADER2));
    message2.setMessageId("message2");
    message2.setEventSource("aws:sqs");
    message2.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue2");

    SQSEvent event = new SQSEvent();
    event.setRecords(asList(message1, message2));

    handler().handleRequest(event, context);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FAAS_INVOCATION_ID, "1-22-333")),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "process" : "aws:sqs process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParentSpanId(trace.getSpan(0).getSpanId())
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(
                                    MESSAGING_OPERATION,
                                    emitOldMessagingSemconv() ? "process" : null),
                                equalTo(MESSAGING_DESTINATION_NAME, null),
                                equalTo(
                                    MESSAGING_OPERATION_NAME,
                                    emitStableMessagingSemconv() ? "process" : null),
                                equalTo(
                                    MESSAGING_OPERATION_TYPE,
                                    emitStableMessagingSemconv() ? "process" : null),
                                equalTo(
                                    MESSAGING_BATCH_MESSAGE_COUNT,
                                    emitStableMessagingSemconv() ? Long.valueOf(2) : null))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .satisfiesExactly(
                                            link(
                                                "53995c3f42cd8ad8",
                                                emitStableMessagingSemconv()
                                                    ? Attributes.of(
                                                        MESSAGING_MESSAGE_ID,
                                                        "message1",
                                                        MESSAGING_DESTINATION_NAME,
                                                        "queue1")
                                                    : Attributes.empty()),
                                            link(
                                                "53995c3f42cd8ad9",
                                                emitStableMessagingSemconv()
                                                    ? Attributes.of(
                                                        MESSAGING_MESSAGE_ID,
                                                        "message2",
                                                        MESSAGING_DESTINATION_NAME,
                                                        "queue2")
                                                    : Attributes.empty())))));
  }

  private static Consumer<LinkData> link(String spanId, Attributes attributes) {
    return linkData -> {
      assertThat(linkData.getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
      assertThat(linkData.getSpanContext().getSpanId()).isEqualTo(spanId);
      assertThat(linkData.getAttributes()).isEqualTo(attributes);
    };
  }

  // Constructor private in early versions.
  private static SQSEvent.SQSMessage newMessage() {
    try {
      Constructor<SQSEvent.SQSMessage> ctor = SQSEvent.SQSMessage.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (ReflectiveOperationException | SecurityException e) {
      throw new AssertionError(e);
    }
  }
}
