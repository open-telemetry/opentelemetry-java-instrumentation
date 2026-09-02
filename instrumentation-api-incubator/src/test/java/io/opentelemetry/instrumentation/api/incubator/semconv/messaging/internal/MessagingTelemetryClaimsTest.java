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

class MessagingTelemetryClaimsTest {

  @Test
  void noneHoldsNothing() {
    MessagingTelemetryClaims claims = MessagingTelemetryClaims.none();

    assertThat(claims.isEmpty()).isTrue();
    for (MessagingOperationType operation : MessagingOperationType.values()) {
      for (MessagingTelemetrySignal signal : MessagingTelemetrySignal.values()) {
        assertThat(claims.contains(operation, signal)).isFalse();
      }
    }
  }

  @Test
  void everyOperationAndSignalPairIsIndependent() {
    for (MessagingOperationType operation : MessagingOperationType.values()) {
      for (MessagingTelemetrySignal signal : MessagingTelemetrySignal.values()) {
        MessagingTelemetryClaims claims = MessagingTelemetryClaims.of(operation, signal);

        for (MessagingOperationType otherOperation : MessagingOperationType.values()) {
          for (MessagingTelemetrySignal otherSignal : MessagingTelemetrySignal.values()) {
            assertThat(claims.contains(otherOperation, otherSignal))
                .as("%s.%s seen from %s.%s", operation, signal, otherOperation, otherSignal)
                .isEqualTo(operation == otherOperation && signal == otherSignal);
          }
        }
      }
    }
  }

  @Test
  void claimingOneSignalLeavesTheOthersOfTheSameOperation() {
    MessagingTelemetryClaims claims = MessagingTelemetryClaims.of(RECEIVE, CONSUMED_MESSAGES);

    assertThat(claims.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(claims.contains(RECEIVE, SPAN)).isFalse();
    assertThat(claims.contains(RECEIVE, CLIENT_OPERATION_DURATION)).isFalse();
  }

  @Test
  void claimingOneOperationLeavesTheSameSignalOfTheOthers() {
    MessagingTelemetryClaims claims =
        MessagingTelemetryClaims.of(SEND, CLIENT_OPERATION_DURATION)
            .with(RECEIVE, CLIENT_OPERATION_DURATION);

    assertThat(claims.contains(SEND, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(claims.contains(RECEIVE, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(claims.contains(PROCESS, CLIENT_OPERATION_DURATION)).isFalse();
  }

  @Test
  void withoutRemovesOnlyTheGivenClaim() {
    MessagingTelemetryClaims claims =
        MessagingTelemetryClaims.of(RECEIVE, SPAN).with(RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetryClaims remaining = claims.without(RECEIVE, SPAN);

    assertThat(remaining.contains(RECEIVE, SPAN)).isFalse();
    assertThat(remaining.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(claims.contains(RECEIVE, SPAN)).isTrue();
  }

  @Test
  void withoutAnUnclaimedSignalChangesNothing() {
    MessagingTelemetryClaims claims = MessagingTelemetryClaims.of(RECEIVE, SPAN);

    assertThat(claims.without(PROCESS, PROCESS_DURATION)).isSameAs(claims);
    assertThat(MessagingTelemetryClaims.none().without(RECEIVE, SPAN))
        .isEqualTo(MessagingTelemetryClaims.none());
  }

  @Test
  void unionHoldsBothSides() {
    MessagingTelemetryClaims union =
        MessagingTelemetryClaims.of(RECEIVE, CONSUMED_MESSAGES)
            .union(MessagingTelemetryClaims.of(PROCESS, PROCESS_DURATION));

    assertThat(union.contains(RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(union.contains(PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(union.contains(PROCESS, SPAN)).isFalse();
  }

  @Test
  void equalSetsAreInterchangeable() {
    MessagingTelemetryClaims claims =
        MessagingTelemetryClaims.of(SEND, SPAN).with(RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryClaims sameClaims =
        MessagingTelemetryClaims.of(RECEIVE, CONSUMED_MESSAGES).with(SEND, SPAN);

    assertThat(claims).isEqualTo(sameClaims).hasSameHashCodeAs(sameClaims);
    assertThat(claims).isNotEqualTo(MessagingTelemetryClaims.of(SEND, SPAN));
    assertThat(claims.with(SEND, SPAN)).isSameAs(claims);
  }

  @Test
  void namesTheClaimsItHolds() {
    assertThat(MessagingTelemetryClaims.of(RECEIVE, CONSUMED_MESSAGES).toString())
        .isEqualTo("MessagingTelemetryClaims[RECEIVE.CONSUMED_MESSAGES]");
    assertThat(MessagingTelemetryClaims.none().toString()).isEqualTo("MessagingTelemetryClaims[]");
  }
}
