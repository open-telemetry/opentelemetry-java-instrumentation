/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import java.util.List;

final class MessagingMetricsAdvice {
  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0));

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
      AttributeKey.stringKey("messaging.destination.template");

  private static final List<AttributeKey<?>> MESSAGING_ATTRIBUTES =
      asList(
          MESSAGING_SYSTEM,
          MESSAGING_DESTINATION_NAME,
          MESSAGING_OPERATION,
          MESSAGING_DESTINATION_PARTITION_ID,
          MESSAGING_DESTINATION_TEMPLATE,
          ErrorAttributes.ERROR_TYPE,
          ServerAttributes.SERVER_PORT,
          ServerAttributes.SERVER_ADDRESS);

  static void applyPublishDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(MESSAGING_ATTRIBUTES);
  }

  static void applyReceiveDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(MESSAGING_ATTRIBUTES);
  }

  static void applyReceiveMessagesAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }
    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(MESSAGING_ATTRIBUTES);
  }

  private MessagingMetricsAdvice() {}
}
