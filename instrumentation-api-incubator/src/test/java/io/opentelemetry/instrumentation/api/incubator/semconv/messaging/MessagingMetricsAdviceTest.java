/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingMetricsAdviceTest {

  @Captor ArgumentCaptor<List<AttributeKey<?>>> attributesCaptor;

  @Test
  void advisesSystemNameForAllMetrics() {
    ExtendedDoubleHistogramBuilder clientDuration = mock(ExtendedDoubleHistogramBuilder.class);
    ExtendedDoubleHistogramBuilder processDuration = mock(ExtendedDoubleHistogramBuilder.class);
    ExtendedLongCounterBuilder sentMessages = mock(ExtendedLongCounterBuilder.class);
    ExtendedLongCounterBuilder consumedMessages = mock(ExtendedLongCounterBuilder.class);

    MessagingMetricsAdvice.applyClientOperationDurationAdvice(clientDuration);
    MessagingMetricsAdvice.applyProcessDurationAdvice(processDuration);
    MessagingMetricsAdvice.applySentMessagesAdvice(sentMessages);
    MessagingMetricsAdvice.applyConsumedMessagesAdvice(consumedMessages);

    verify(clientDuration).setAttributesAdvice(attributesCaptor.capture());
    verify(processDuration).setAttributesAdvice(attributesCaptor.capture());
    verify(sentMessages).setAttributesAdvice(attributesCaptor.capture());
    verify(consumedMessages).setAttributesAdvice(attributesCaptor.capture());
    assertThat(attributesCaptor.getAllValues())
        .allSatisfy(
            attributes -> assertThat(attributes).contains(stringKey("messaging.system.name")));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void filtersHighCardinalityDestinationNames() {
    Attributes lowCardinality = Attributes.of(MESSAGING_DESTINATION_NAME, "orders");
    assertThat(MessagingMetricsAdvice.filterAttributes(lowCardinality)).isSameAs(lowCardinality);

    assertThat(
            MessagingMetricsAdvice.filterAttributes(
                Attributes.builder()
                    .put(MESSAGING_DESTINATION_NAME, "orders-42")
                    .put(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}")
                    .build()))
        .isEqualTo(Attributes.of(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}"));
    assertThat(
            MessagingMetricsAdvice.filterAttributes(
                Attributes.builder()
                    .put(MESSAGING_DESTINATION_NAME, "tmp-42")
                    .put(MESSAGING_DESTINATION_TEMPORARY, true)
                    .build()))
        .isEqualTo(Attributes.of(MESSAGING_DESTINATION_TEMPORARY, true));
    assertThat(
            MessagingMetricsAdvice.filterAttributes(
                Attributes.builder()
                    .put(MESSAGING_DESTINATION_NAME, "generated-42")
                    .put(MESSAGING_DESTINATION_ANONYMOUS, true)
                    .build()))
        .isEqualTo(Attributes.of(MESSAGING_DESTINATION_ANONYMOUS, true));
  }
}
