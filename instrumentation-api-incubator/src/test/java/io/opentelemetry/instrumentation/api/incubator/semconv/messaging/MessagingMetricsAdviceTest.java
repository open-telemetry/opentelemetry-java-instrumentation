/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;

import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

class MessagingMetricsAdviceTest {

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
