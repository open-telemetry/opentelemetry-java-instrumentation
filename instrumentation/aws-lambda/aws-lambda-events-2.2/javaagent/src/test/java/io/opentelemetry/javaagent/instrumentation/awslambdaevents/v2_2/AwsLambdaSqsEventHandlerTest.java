/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.AbstractAwsLambdaSqsEventHandlerTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AwsLambdaSqsEventHandlerTest extends AbstractAwsLambdaSqsEventHandlerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected RequestHandler<SQSEvent, Void> handler() {
    return new TestRequestHandler();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected String instrumentationName() {
    return "io.opentelemetry.aws-lambda-events-2.2";
  }

  @Test
  void sameNestedEventStartsOneProcessingSpan() {
    SQSEvent event = newEvent("message");

    new NestedTestRequestHandler(event).handleRequest(event, newContext());

    testing.waitForTraces(2);
    assertThat(testing.spans())
        .filteredOn(span -> span.getKind() == SpanKind.CONSUMER)
        .hasSize(1);
  }

  @Test
  void distinctNestedEventStartsTwoProcessingSpans() {
    SQSEvent outerEvent = newEvent("outer");
    SQSEvent nestedEvent = newEvent("nested");

    new NestedTestRequestHandler(nestedEvent).handleRequest(outerEvent, newContext());

    testing.waitForTraces(2);
    assertThat(testing.spans())
        .filteredOn(span -> span.getKind() == SpanKind.CONSUMER)
        .hasSize(2);
  }

  private static Context newContext() {
    Context context = mock(Context.class);
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
    return context;
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
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (ReflectiveOperationException | SecurityException e) {
      throw new AssertionError(e);
    }
  }

  private static class TestRequestHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return null;
    }
  }

  private static class NestedTestRequestHandler implements RequestHandler<SQSEvent, Void> {
    private final SQSEvent nestedEvent;
    private final RequestHandler<SQSEvent, Void> delegate = new TestRequestHandler();

    private NestedTestRequestHandler(SQSEvent nestedEvent) {
      this.nestedEvent = nestedEvent;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return delegate.handleRequest(nestedEvent, context);
    }
  }
}
