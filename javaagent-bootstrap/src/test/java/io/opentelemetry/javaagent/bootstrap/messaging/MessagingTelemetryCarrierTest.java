/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import org.junit.jupiter.api.Test;

class MessagingTelemetryCarrierTest {

  @Test
  void remembersClaimsPerObject() {
    Object message = new Object();
    Object otherMessage = new Object();

    MessagingTelemetryCarrier.claim(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(MessagingTelemetryCarrier.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(otherMessage, RECEIVE, CONSUMED_MESSAGES))
        .isFalse();
  }

  @Test
  void keepsSignalsOfTheSameObjectIndependent() {
    Object message = new Object();

    MessagingTelemetryCarrier.claim(message, RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryCarrier.claim(message, PROCESS, SPAN);

    assertThat(MessagingTelemetryCarrier.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(message, PROCESS, SPAN)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(message, RECEIVE, SPAN)).isFalse();
  }

  @Test
  void mergeAddsToWhatTheTargetAlreadyHolds() {
    Object source = new Object();
    Object target = new Object();
    MessagingTelemetryCarrier.claim(source, RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryCarrier.claim(target, PROCESS, PROCESS_DURATION);

    MessagingTelemetryCarrier.merge(source, target);

    assertThat(MessagingTelemetryCarrier.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(target, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(source, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void mergingAnUnclaimedSourceKeepsTheTargetAsItWas() {
    Object target = new Object();
    MessagingTelemetryCarrier.claim(target, RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetryCarrier.merge(new Object(), target);

    assertThat(MessagingTelemetryCarrier.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replaceMakesTheTargetMatchTheSource() {
    Object source = new Object();
    Object target = new Object();
    MessagingTelemetryCarrier.claim(source, RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryCarrier.claim(target, PROCESS, PROCESS_DURATION);

    MessagingTelemetryCarrier.replace(source, target);

    assertThat(MessagingTelemetryCarrier.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(MessagingTelemetryCarrier.isClaimed(target, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void replacingFromAnUnclaimedSourceEmptiesTheTarget() {
    Object target = new Object();
    MessagingTelemetryCarrier.claim(target, RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetryCarrier.replace(new Object(), target);

    assertThat(MessagingTelemetryCarrier.getClaims(target).isEmpty()).isTrue();
  }

  @Test
  void clearForgetsEverythingAboutTheObject() {
    Object message = new Object();
    MessagingTelemetryCarrier.claim(message, RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryCarrier.claim(message, PROCESS, SPAN);

    MessagingTelemetryCarrier.clear(message);

    assertThat(MessagingTelemetryCarrier.getClaims(message).isEmpty()).isTrue();
  }

  @Test
  void toleratesNullObjects() {
    Object message = new Object();
    MessagingTelemetryCarrier.claim(message, RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetryCarrier.claim(null, RECEIVE, CONSUMED_MESSAGES);
    MessagingTelemetryCarrier.merge(message, null);
    MessagingTelemetryCarrier.merge(null, message);
    MessagingTelemetryCarrier.replace(message, null);
    MessagingTelemetryCarrier.replace(null, null);
    MessagingTelemetryCarrier.clear(null);

    assertThat(MessagingTelemetryCarrier.getClaims(null))
        .isEqualTo(MessagingTelemetryClaims.none());
    assertThat(MessagingTelemetryCarrier.isClaimed(null, RECEIVE, CONSUMED_MESSAGES)).isFalse();
    assertThat(MessagingTelemetryCarrier.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replacingFromNullEmptiesTheTarget() {
    Object target = new Object();
    MessagingTelemetryCarrier.claim(target, RECEIVE, CONSUMED_MESSAGES);

    MessagingTelemetryCarrier.replace(null, target);

    assertThat(MessagingTelemetryCarrier.getClaims(target).isEmpty()).isTrue();
  }
}
