/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaSqsMetricsAssertions.assertMetrics;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AwsLambdaSqsNestedEventHandlerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
  }

  @Test
  void distinctNestedEventStartsProcessing() {
    SQSEvent outerEvent = newEvent("outer");
    SQSEvent nestedEvent = newEvent("nested");

    SQSBatchResponse result =
        new NestedHandler(testing.getOpenTelemetrySdk(), nestedEvent)
            .handleRequest(outerEvent, context);

    assertThat(result).isNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("my_function").hasKind(SpanKind.SERVER),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "process queue1" : "aws:sqs process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParentSpanId(trace.getSpan(0).getSpanId()),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "process queue1" : "aws:sqs process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParentSpanId(trace.getSpan(1).getSpanId())));
    assertMetrics(testing, TracingSqsEventHandler.INSTRUMENTATION_NAME, "queue1", 2, 2, null);
  }

  private static SQSEvent newEvent(String messageId) {
    SQSEvent.SQSMessage message = newMessage();
    message.setAttributes(emptyMap());
    message.setMessageId(messageId);
    message.setEventSource("aws:sqs");
    message.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");

    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(message));
    return event;
  }

  private static SQSEvent.SQSMessage newMessage() {
    try {
      Constructor<SQSEvent.SQSMessage> ctor = SQSEvent.SQSMessage.class.getDeclaredConstructor();
      return ctor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static class NestedHandler extends TracingSqsEventHandler {
    private final SQSEvent nestedEvent;
    private final TracingSqsEventHandler delegate;

    NestedHandler(OpenTelemetrySdk openTelemetrySdk, SQSEvent nestedEvent) {
      super(openTelemetrySdk);
      this.nestedEvent = nestedEvent;
      delegate = new TestHandler(openTelemetrySdk);
    }

    @Override
    protected SQSBatchResponse handleEvent(SQSEvent event, Context context) {
      return delegate.doHandleRequest(nestedEvent, context);
    }
  }

  private static class TestHandler extends TracingSqsEventHandler {

    TestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk);
    }

    @Override
    protected SQSBatchResponse handleEvent(SQSEvent event, Context context) {
      return null;
    }
  }
}
