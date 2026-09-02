/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.SEND;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CLIENT_OPERATION_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class MessagingTelemetryStateTest {

  @Test
  void tracksNothingUntilAnInstrumentationOptsIn() {
    Context context = Context.root();

    assertThat(MessagingTelemetryState.isEnabled(context)).isFalse();
    assertThat(MessagingTelemetryState.isClaimed(context, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void claimIfEnabledIsIgnoredWithoutOptIn() {
    Context context =
        MessagingTelemetryState.claimIfEnabled(Context.root(), RECEIVE, CONSUMED_MESSAGES);

    assertThat(MessagingTelemetryState.isEnabled(context)).isFalse();
    assertThat(MessagingTelemetryState.isClaimed(context, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void claimIfEnabledIsRememberedAfterOptIn() {
    Context context = MessagingTelemetryState.enable(Context.root());
    context = MessagingTelemetryState.claimIfEnabled(context, RECEIVE, CONSUMED_MESSAGES);

    assertThat(MessagingTelemetryState.isClaimed(context, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void claimOptsInOnItsOwn() {
    Context context = MessagingTelemetryState.claim(Context.root(), PROCESS, PROCESS_DURATION);

    assertThat(MessagingTelemetryState.isEnabled(context)).isTrue();
    assertThat(MessagingTelemetryState.isClaimed(context, PROCESS, PROCESS_DURATION)).isTrue();
  }

  @Test
  void enablingTwiceKeepsWhatWasAlreadyClaimed() {
    Context context = MessagingTelemetryState.claim(Context.root(), PROCESS, PROCESS_DURATION);

    Context enabled = MessagingTelemetryState.enable(context);

    assertThat(enabled).isSameAs(context);
    assertThat(MessagingTelemetryState.isClaimed(enabled, PROCESS, PROCESS_DURATION)).isTrue();
  }

  @Test
  void separateOperationsAndSignalsStayIndependent() {
    Context context = MessagingTelemetryState.enable(Context.root());
    context = MessagingTelemetryState.claimIfEnabled(context, SEND, CLIENT_OPERATION_DURATION);

    assertThat(MessagingTelemetryState.isClaimed(context, SEND, CLIENT_OPERATION_DURATION))
        .isTrue();
    assertThat(MessagingTelemetryState.isClaimed(context, RECEIVE, CLIENT_OPERATION_DURATION))
        .isFalse();
    assertThat(MessagingTelemetryState.isClaimed(context, SEND, SPAN)).isFalse();
  }

  @Test
  void aClaimOnlyReachesTheContextItWasMadeOn() {
    Context outer = MessagingTelemetryState.enable(Context.root());
    Context inner = MessagingTelemetryState.claimIfEnabled(outer, PROCESS, PROCESS_DURATION);

    assertThat(MessagingTelemetryState.isClaimed(inner, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(MessagingTelemetryState.isClaimed(outer, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void anUnknownOperationClaimsNothing() {
    Context context = MessagingTelemetryState.enable(Context.root());

    Context unchanged = MessagingTelemetryState.claim(context, null, CONSUMED_MESSAGES);

    assertThat(unchanged).isSameAs(context);
    assertThat(MessagingTelemetryState.isClaimed(context, null, CONSUMED_MESSAGES)).isFalse();
  }
}
