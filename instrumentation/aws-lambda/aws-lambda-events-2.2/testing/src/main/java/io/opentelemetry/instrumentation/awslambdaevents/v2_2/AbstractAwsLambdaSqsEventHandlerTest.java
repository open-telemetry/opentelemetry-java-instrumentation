/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
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

  protected abstract RequestHandler<SQSEvent, Void> handler();

  protected abstract InstrumentationExtension testing();

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
    message1.setAttributes(Collections.singletonMap("AWSTraceHeader", AWS_TRACE_HEADER));
    message1.setMessageId("message1");
    message1.setEventSource("queue1");

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setAttributes(Collections.emptyMap());
    message2.setMessageId("message2");
    message2.setEventSource("queue1");

    SQSEvent event = new SQSEvent();
    event.setRecords(Arrays.asList(message1, message2));

    handler().handleRequest(event, context);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333")),
                    span ->
                        span.hasName("queue1 process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParentSpanId(trace.getSpan(0).getSpanId())
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    MESSAGING_SYSTEM,
                                    MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                        .AWS_SQS),
                                equalTo(MESSAGING_OPERATION, "process"))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link -> {
                                              assertThat(link.getSpanContext().getTraceId())
                                                  .isEqualTo("5759e988bd862e3fe1be46a994272793");
                                              assertThat(link.getSpanContext().getSpanId())
                                                  .isEqualTo("53995c3f42cd8ad8");
                                            }))));
  }

  @Test
  void differentSource() {
    SQSEvent.SQSMessage message1 = newMessage();
    message1.setAttributes(Collections.singletonMap("AWSTraceHeader", AWS_TRACE_HEADER));
    message1.setMessageId("message1");
    message1.setEventSource("queue1");

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setAttributes(Collections.emptyMap());
    message2.setMessageId("message2");
    message2.setEventSource("queue2");

    SQSEvent event = new SQSEvent();
    event.setRecords(Arrays.asList(message1, message2));

    handler().handleRequest(event, context);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333")),
                    span ->
                        span.hasName("multiple_sources process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParentSpanId(trace.getSpan(0).getSpanId())
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    MESSAGING_SYSTEM,
                                    MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                        .AWS_SQS),
                                equalTo(MESSAGING_OPERATION, "process"))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link -> {
                                              assertThat(link.getSpanContext().getTraceId())
                                                  .isEqualTo("5759e988bd862e3fe1be46a994272793");
                                              assertThat(link.getSpanContext().getSpanId())
                                                  .isEqualTo("53995c3f42cd8ad8");
                                            }))));
  }

  // Constructor private in early versions.
  private static SQSEvent.SQSMessage newMessage() {
    try {
      Constructor<SQSEvent.SQSMessage> ctor = SQSEvent.SQSMessage.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }
}
