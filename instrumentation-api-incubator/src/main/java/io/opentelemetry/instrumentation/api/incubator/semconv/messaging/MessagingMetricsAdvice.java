/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.List;

public class MessagingMetricsAdvice {
  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0));

  static void applyPublishDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT,
                ErrorAttributes.ERROR_TYPE,
                ServerAttributes.SERVER_PORT,
                ServerAttributes.SERVER_ADDRESS));
  }

  private MessagingMetricsAdvice() {}
}
