/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.SEND;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class MessagingMetricSuppressionTest {

  @Test
  void tracksNothingUntilAnInstrumentationOptsIn() {
    Context context = Context.root();

    assertThat(MessagingMetricSuppression.isEnabled(context)).isFalse();
    assertThat(MessagingMetricSuppression.isConsumedMessagesSuppressed(context)).isFalse();
  }

  @Test
  void suppressingIsIgnoredWithoutOptIn() {
    Context context = MessagingMetricSuppression.suppressConsumedMessages(Context.root());

    assertThat(context).isSameAs(Context.root());
    assertThat(MessagingMetricSuppression.isEnabled(context)).isFalse();
    assertThat(MessagingMetricSuppression.isConsumedMessagesSuppressed(context)).isFalse();
  }

  @Test
  void suppressesConsumedMessagesAfterOptIn() {
    Context context = MessagingMetricSuppression.enable(Context.root());
    context = MessagingMetricSuppression.suppressConsumedMessages(context);

    assertThat(MessagingMetricSuppression.isConsumedMessagesSuppressed(context)).isTrue();
  }

  @Test
  void enablingTwiceKeepsSuppressedMetrics() {
    Context context = MessagingMetricSuppression.enable(Context.root());
    context = MessagingMetricSuppression.suppressProcessDuration(context);

    Context enabled = MessagingMetricSuppression.enable(context);

    assertThat(enabled).isSameAs(context);
    assertThat(MessagingMetricSuppression.isProcessDurationSuppressed(enabled)).isTrue();
  }

  @Test
  void metricSignalsAndOperationTypesStayIndependent() {
    Context context = MessagingMetricSuppression.enable(Context.root());
    context = MessagingMetricSuppression.suppressClientOperationDuration(context, SEND);
    context = MessagingMetricSuppression.suppressProcessDuration(context);
    context = MessagingMetricSuppression.suppressSentMessages(context);

    assertThat(MessagingMetricSuppression.isClientOperationDurationSuppressed(context, SEND))
        .isTrue();
    assertThat(MessagingMetricSuppression.isClientOperationDurationSuppressed(context, RECEIVE))
        .isFalse();
    assertThat(MessagingMetricSuppression.isProcessDurationSuppressed(context)).isTrue();
    assertThat(MessagingMetricSuppression.isSentMessagesSuppressed(context)).isTrue();
    assertThat(MessagingMetricSuppression.isConsumedMessagesSuppressed(context)).isFalse();
  }

  @Test
  void suppressedMetricOnlyReachesItsContext() {
    Context outer = MessagingMetricSuppression.enable(Context.root());
    Context inner = MessagingMetricSuppression.suppressProcessDuration(outer);

    assertThat(MessagingMetricSuppression.isProcessDurationSuppressed(inner)).isTrue();
    assertThat(MessagingMetricSuppression.isProcessDurationSuppressed(outer)).isFalse();
  }

  @Test
  void unknownClientOperationSuppressesNothing() {
    Context context = MessagingMetricSuppression.enable(Context.root());

    Context unchanged = MessagingMetricSuppression.suppressClientOperationDuration(context, null);

    assertThat(unchanged).isSameAs(context);
    assertThat(MessagingMetricSuppression.isClientOperationDurationSuppressed(context, null))
        .isFalse();
  }
}
