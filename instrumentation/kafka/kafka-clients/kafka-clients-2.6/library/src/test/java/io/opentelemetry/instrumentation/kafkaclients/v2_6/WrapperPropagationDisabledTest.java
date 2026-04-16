/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class WrapperPropagationDisabledTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setPropagationEnabled(false);
  }

  @Override
  void assertTraces(boolean testHeaders) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(testHeaders)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks()
                        .hasAttributesSatisfyingExactly(processAttributes(greeting, testHeaders)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static List<AttributeAssertion> sendAttributes(boolean testHeaders) {
    return WrapperSuppressReceiveSpansTest.sendAttributes(testHeaders);
  }

  private static List<AttributeAssertion> processAttributes(String greeting, boolean testHeaders) {
    return WrapperSuppressReceiveSpansTest.processAttributes(greeting, testHeaders);
  }
}
