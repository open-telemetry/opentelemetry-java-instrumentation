/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

@SuppressWarnings("deprecation") // using deprecated semconv
class WrapperSuppressReceiveSpansTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setMessagingReceiveTelemetryEnabled(false);
  }

  @Override
  void assertTraces(boolean testHeaders, boolean testExperimental) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "send " + SHARED_TOPIC
                                : SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(testHeaders, testExperimental)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(greeting, testHeaders, testExperimental)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  static List<AttributeAssertion> sendAttributes(boolean testHeaders, boolean testExperimental) {
    return WrapperTest.sendAttributes(testHeaders, testExperimental);
  }

  static List<AttributeAssertion> processAttributes(
      String greeting, boolean testHeaders, boolean testExperimental) {
    return WrapperTest.processAttributes(greeting, testHeaders, testExperimental);
  }
}
