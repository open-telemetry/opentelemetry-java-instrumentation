/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.AbstractLongAssert;

@SuppressWarnings("deprecation") // old semconv
class InterceptorsSuppressReceiveSpansTest extends AbstractInterceptorsTest {

  @Override
  void assertTraces() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("producer"))),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                greeting.getBytes(StandardCharsets.UTF_8).length),
                            satisfies(
                                SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                AbstractLongAssert::isNotNegative),
                            equalTo(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))),
        // ideally we'd want producer callback to be part of the main trace, we just aren't able to
        // instrument that
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("producer callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }
}
