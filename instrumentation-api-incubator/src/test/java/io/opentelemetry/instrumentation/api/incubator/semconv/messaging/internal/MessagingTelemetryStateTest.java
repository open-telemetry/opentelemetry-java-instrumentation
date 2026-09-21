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
    assertThat(MessagingTelemetryState.contains(context, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void addIfEnabledIsIgnoredWithoutOptIn() {
    Context context =
        MessagingTelemetryState.addIfEnabled(Context.root(), RECEIVE, CONSUMED_MESSAGES);

    assertThat(MessagingTelemetryState.isEnabled(context)).isFalse();
    assertThat(MessagingTelemetryState.contains(context, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void addIfEnabledIsRememberedAfterOptIn() {
    Context context = MessagingTelemetryState.enable(Context.root());
    context = MessagingTelemetryState.addIfEnabled(context, RECEIVE, CONSUMED_MESSAGES);

    assertThat(MessagingTelemetryState.contains(context, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void addEnablesTracking() {
    Context context = MessagingTelemetryState.add(Context.root(), PROCESS, PROCESS_DURATION);

    assertThat(MessagingTelemetryState.isEnabled(context)).isTrue();
    assertThat(MessagingTelemetryState.contains(context, PROCESS, PROCESS_DURATION)).isTrue();
  }

  @Test
  void enablingTwiceKeepsSignalsAlreadyPresent() {
    Context context = MessagingTelemetryState.add(Context.root(), PROCESS, PROCESS_DURATION);

    Context enabled = MessagingTelemetryState.enable(context);

    assertThat(enabled).isSameAs(context);
    assertThat(MessagingTelemetryState.contains(enabled, PROCESS, PROCESS_DURATION)).isTrue();
  }

  @Test
  void separateOperationsAndSignalsStayIndependent() {
    Context context = MessagingTelemetryState.enable(Context.root());
    context = MessagingTelemetryState.addIfEnabled(context, SEND, CLIENT_OPERATION_DURATION);

    assertThat(MessagingTelemetryState.contains(context, SEND, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(MessagingTelemetryState.contains(context, RECEIVE, CLIENT_OPERATION_DURATION))
        .isFalse();
    assertThat(MessagingTelemetryState.contains(context, SEND, SPAN)).isFalse();
  }

  @Test
  void addedSignalOnlyReachesItsContext() {
    Context outer = MessagingTelemetryState.enable(Context.root());
    Context inner = MessagingTelemetryState.addIfEnabled(outer, PROCESS, PROCESS_DURATION);

    assertThat(MessagingTelemetryState.contains(inner, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(MessagingTelemetryState.contains(outer, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void anUnknownOperationAddsNothing() {
    Context context = MessagingTelemetryState.enable(Context.root());

    Context unchanged = MessagingTelemetryState.add(context, null, CONSUMED_MESSAGES);

    assertThat(unchanged).isSameAs(context);
    assertThat(MessagingTelemetryState.contains(context, null, CONSUMED_MESSAGES)).isFalse();
  }
}
