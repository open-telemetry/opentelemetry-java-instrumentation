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

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import org.junit.jupiter.api.Test;

class MessagingTelemetrySignalsTest {

  @Test
  void noneHoldsNothing() {
    MessagingTelemetrySignals signals = MessagingTelemetrySignals.none();

    assertThat(signals.isEmpty()).isTrue();
    for (MessagingOperationType operation : MessagingOperationType.values()) {
      for (MessagingTelemetrySignal signal : MessagingTelemetrySignal.values()) {
        assertThat(signals.contains(operation, signal)).isFalse();
      }
    }
  }

  @Test
  void everyOperationAndSignalPairIsIndependent() {
    for (MessagingOperationType operation : MessagingOperationType.values()) {
      for (MessagingTelemetrySignal signal : MessagingTelemetrySignal.values()) {
        MessagingTelemetrySignals signals = MessagingTelemetrySignals.of(operation, signal);

        for (MessagingOperationType otherOperation : MessagingOperationType.values()) {
          for (MessagingTelemetrySignal otherSignal : MessagingTelemetrySignal.values()) {
            assertThat(signals.contains(otherOperation, otherSignal))
                .isEqualTo(operation == otherOperation && signal == otherSignal);
          }
        }
      }
    }
  }

  @Test
  void addingOneSignalLeavesTheOthersOfTheSameOperation() {
    MessagingTelemetrySignals signals = MessagingTelemetrySignals.of(RECEIVE, CONSUMED_MESSAGES);

    assertThat(signals.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(signals.contains(RECEIVE, SPAN)).isFalse();
    assertThat(signals.contains(RECEIVE, CLIENT_OPERATION_DURATION)).isFalse();
  }

  @Test
  void addingOneOperationLeavesTheSameSignalOfTheOthers() {
    MessagingTelemetrySignals signals =
        MessagingTelemetrySignals.of(SEND, CLIENT_OPERATION_DURATION)
            .with(RECEIVE, CLIENT_OPERATION_DURATION);

    assertThat(signals.contains(SEND, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(signals.contains(RECEIVE, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(signals.contains(PROCESS, CLIENT_OPERATION_DURATION)).isFalse();
  }

  @Test
  void withoutRemovesOnlyTheGivenSignal() {
    MessagingTelemetrySignals signals =
        MessagingTelemetrySignals.of(RECEIVE, SPAN).with(RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetrySignals remaining = signals.without(RECEIVE, SPAN);

    assertThat(remaining.contains(RECEIVE, SPAN)).isFalse();
    assertThat(remaining.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(signals.contains(RECEIVE, SPAN)).isTrue();
  }

  @Test
  void withoutAnAbsentSignalChangesNothing() {
    MessagingTelemetrySignals signals = MessagingTelemetrySignals.of(RECEIVE, SPAN);

    assertThat(signals.without(PROCESS, PROCESS_DURATION)).isSameAs(signals);
    assertThat(MessagingTelemetrySignals.none().without(RECEIVE, SPAN))
        .isEqualTo(MessagingTelemetrySignals.none());
  }

  @Test
  void unionHoldsBothSides() {
    MessagingTelemetrySignals union =
        MessagingTelemetrySignals.of(RECEIVE, CONSUMED_MESSAGES)
            .union(MessagingTelemetrySignals.of(PROCESS, PROCESS_DURATION));

    assertThat(union.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(union.contains(PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(union.contains(PROCESS, SPAN)).isFalse();
  }

  @Test
  void equalSetsAreInterchangeable() {
    MessagingTelemetrySignals signals =
        MessagingTelemetrySignals.of(SEND, SPAN).with(RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetrySignals sameSignals =
        MessagingTelemetrySignals.of(RECEIVE, CONSUMED_MESSAGES).with(SEND, SPAN);

    assertThat(signals).isEqualTo(sameSignals).hasSameHashCodeAs(sameSignals);
    assertThat(signals).isNotEqualTo(MessagingTelemetrySignals.of(SEND, SPAN));
    assertThat(signals.with(SEND, SPAN)).isSameAs(signals);
  }

  @Test
  void namesTheSignalsItHolds() {
    assertThat(MessagingTelemetrySignals.of(RECEIVE, CONSUMED_MESSAGES).toString())
        .isEqualTo("MessagingTelemetrySignals[RECEIVE.CONSUMED_MESSAGES]");
    assertThat(MessagingTelemetrySignals.none().toString())
        .isEqualTo("MessagingTelemetrySignals[]");
  }
}
