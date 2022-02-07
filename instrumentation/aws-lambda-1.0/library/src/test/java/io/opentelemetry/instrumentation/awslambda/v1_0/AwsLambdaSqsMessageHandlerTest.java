/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AwsLambdaSqsMessageHandlerTest {

  private static final String AWS_TRACE_HEADER1 =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";
  private static final String AWS_TRACE_HEADER2 =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad9;Sampled=1";

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void processSpans() {
    SQSEvent.SQSMessage message1 = newMessage();
    message1.setAttributes(Collections.singletonMap("AWSTraceHeader", AWS_TRACE_HEADER1));
    message1.setMessageId("message1");
    message1.setEventSource("queue1");

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setAttributes(Collections.singletonMap("AWSTraceHeader", AWS_TRACE_HEADER2));
    message2.setMessageId("message2");
    message2.setEventSource("queue1");

    SQSEvent event = new SQSEvent();
    event.setRecords(Arrays.asList(message1, message2));

    new TestHandler(testing.getOpenTelemetrySdk()).handleRequest(event, context);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsOnly(
                                        entry(SemanticAttributes.FAAS_EXECUTION, "1-22-333"))),
                span ->
                    span.hasName("queue1 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParentSpanId(trace.getSpan(0).getSpanId())
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsOnly(
                                        entry(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                                        entry(SemanticAttributes.MESSAGING_OPERATION, "process")))
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "5759e988bd862e3fe1be46a994272793",
                                    "53995c3f42cd8ad8",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())),
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "5759e988bd862e3fe1be46a994272793",
                                    "53995c3f42cd8ad9",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault()))),
                span ->
                    span.hasName("queue1 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParentSpanId(trace.getSpan(1).getSpanId())
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsOnly(
                                        entry(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                                        entry(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                        entry(SemanticAttributes.MESSAGING_MESSAGE_ID, "message1"),
                                        entry(SemanticAttributes.MESSAGING_DESTINATION, "queue1")))
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "5759e988bd862e3fe1be46a994272793",
                                    "53995c3f42cd8ad8",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault()))),
                span ->
                    span.hasName("queue1 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParentSpanId(trace.getSpan(1).getSpanId())
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsOnly(
                                        entry(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                                        entry(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                        entry(SemanticAttributes.MESSAGING_MESSAGE_ID, "message2"),
                                        entry(SemanticAttributes.MESSAGING_DESTINATION, "queue1")))
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "5759e988bd862e3fe1be46a994272793",
                                    "53995c3f42cd8ad9",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))));
  }

  // Constructor private in early versions.
  private static SQSEvent.SQSMessage newMessage() {
    try {
      Constructor<SQSEvent.SQSMessage> ctor = SQSEvent.SQSMessage.class.getDeclaredConstructor();
      return ctor.newInstance();
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  private static final class TestHandler extends TracingSqsMessageHandler {

    TestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk);
    }

    @Override
    protected void handleMessage(SQSEvent.SQSMessage message, Context context) {}
  }
}
