/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AwsLambdaSqsInstrumenterFactoryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void batchAndMessageProcessingSelectMessagingSchemaWithoutChangingSuppression() {
    Instrumenter<SQSEvent, Void> batchInstrumenter =
        AwsLambdaSqsInstrumenterFactory.forEvent(testing.getOpenTelemetry(), "test-batch");
    Instrumenter<SQSMessage, Boolean> messageInstrumenter =
        AwsLambdaSqsInstrumenterFactory.forSelectedMessageProcessing(
            testing.getOpenTelemetry(), "test-message");
    SQSMessage message = new SQSMessage();
    message.setMessageId("message-id");
    message.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue");
    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(message));

    Context batchContext = batchInstrumenter.start(Context.root(), event);
    assertThat(messageInstrumenter.shouldStart(batchContext, message)).isTrue();
    Context messageContext = messageInstrumenter.start(batchContext, message);
    assertThat(messageInstrumenter.shouldStart(messageContext, message)).isFalse();
    assertThat(batchInstrumenter.shouldStart(messageContext, event)).isTrue();
    messageInstrumenter.end(messageContext, message, true, null);
    batchInstrumenter.end(batchContext, event, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process queue")
                        .hasNoParent()
                        .hasAttribute(equalTo(MESSAGING_OPERATION_NAME, "process"))
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getInstrumentationScopeInfo().getSchemaUrl())
                                    .isEqualTo("https://opentelemetry.io/schemas/1.43.0")),
                span ->
                    span.hasName("process queue")
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(equalTo(MESSAGING_OPERATION_NAME, "process"))
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getInstrumentationScopeInfo().getSchemaUrl())
                                    .isEqualTo("https://opentelemetry.io/schemas/1.43.0"))));
    testing.waitAndAssertMetrics(
        "test-batch",
        metric ->
            metric
                .hasName("messaging.process.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getInstrumentationScopeInfo().getSchemaUrl())
                            .isEqualTo("https://opentelemetry.io/schemas/1.43.0")),
        metric ->
            metric
                .hasName("messaging.client.consumed.messages")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getInstrumentationScopeInfo().getSchemaUrl())
                            .isEqualTo("https://opentelemetry.io/schemas/1.43.0")));
    testing.waitAndAssertMetrics(
        "test-message",
        metric ->
            metric
                .hasName("messaging.process.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getInstrumentationScopeInfo().getSchemaUrl())
                            .isEqualTo("https://opentelemetry.io/schemas/1.43.0")));
  }
}
