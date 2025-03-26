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
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
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
        "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaSqsEventWrapperTest$TestRequestHandler::handleRequest")
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

  @SuppressWarnings("deprecation") // using deprecated semconv
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333")),
                span ->
                    span.hasName("otel process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                MESSAGING_SYSTEM,
                                MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                    .AWS_SQS),
                            equalTo(MESSAGING_OPERATION, "process"))));
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
