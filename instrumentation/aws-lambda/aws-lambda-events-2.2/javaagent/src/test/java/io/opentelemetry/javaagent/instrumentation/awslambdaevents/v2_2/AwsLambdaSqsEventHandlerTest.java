/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

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
  void sameNestedEventStartsSingleProcessingSpan() {
    SQSEvent event = new SQSEvent();

    assertNestedProcessingSpanCount(event, event, 1);
  }

  @Test
  void distinctNestedEventStartsSeparateProcessingSpans() {
    assertNestedProcessingSpanCount(new SQSEvent(), new SQSEvent(), 2);
  }

  private static void assertNestedProcessingSpanCount(
      SQSEvent outerEvent, SQSEvent nestedEvent, long expectedCount) {
    Context context = mock(Context.class);
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");

    new NestedRequestHandler(nestedEvent).handleRequest(outerEvent, context);

    long processingSpanCount =
        testing.waitForTraces(2).stream()
            .flatMap(trace -> trace.stream())
            .filter(span -> span.getKind() == SpanKind.CONSUMER)
            .count();
    assertThat(processingSpanCount).isEqualTo(expectedCount);
  }

  private static class TestRequestHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return null;
    }
  }

  private static class NestedRequestHandler implements RequestHandler<SQSEvent, Void> {
    private final SQSEvent nestedEvent;
    private final RequestHandler<SQSEvent, Void> delegate = new TestRequestHandler();

    private NestedRequestHandler(SQSEvent nestedEvent) {
      this.nestedEvent = nestedEvent;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return delegate.handleRequest(nestedEvent, context);
    }
  }
}
