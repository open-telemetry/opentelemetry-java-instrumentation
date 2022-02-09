/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Constructor;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SetEnvironmentVariable(
    key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
    value =
        "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaSqsEventWrapperTest$TestRequestHandler::handleRequest")
public class AwsLambdaSqsEventWrapperTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
    when(context.getInvokedFunctionArn())
        .thenReturn("arn:aws:lambda:us-east-1:123456789:function:test");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void eventTraced() {
    SQSEvent event = new SQSEvent();
    SQSEvent.SQSMessage record = newMessage();
    record.setEventSource("otel");
    record.setAttributes(Collections.emptyMap());
    event.setRecords(Collections.singletonList(record));

    TracingSqsEventWrapper wrapper =
        new TracingSqsEventWrapper(
            testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    wrapper.handleRequest(event, context);

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
                                        entry(
                                            ResourceAttributes.FAAS_ID,
                                            "arn:aws:lambda:us-east-1:123456789:function:test"),
                                        entry(ResourceAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                                        entry(SemanticAttributes.FAAS_EXECUTION, "1-22-333"))),
                span ->
                    span.hasName("otel process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsOnly(
                                        entry(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS"),
                                        entry(
                                            SemanticAttributes.MESSAGING_OPERATION, "process")))));
  }

  public static final class TestRequestHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return null;
    }
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
}
