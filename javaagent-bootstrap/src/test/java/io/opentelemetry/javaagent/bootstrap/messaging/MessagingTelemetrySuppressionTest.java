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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MessagingTelemetrySuppressionTest {

  @Test
  void claimsNothingUntilAskedTo() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    assertThat(suppression.current()).isEqualTo(MessagingTelemetryClaims.none());
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isFalse();
  }

  @Test
  void suppressReturnsWhatWasClaimedBefore() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    MessagingTelemetryClaims previous = suppression.suppress(PROCESS, SPAN);

    assertThat(previous).isEqualTo(MessagingTelemetryClaims.none());
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();

    suppression.restore(previous);
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isFalse();
  }

  @Test
  void keepsSignalsAndOperationsIndependent() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    suppression.suppress(PROCESS, SPAN);

    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(suppression.isSuppressed(PROCESS, PROCESS_DURATION)).isFalse();
    assertThat(suppression.isSuppressed(RECEIVE, SPAN)).isFalse();

    suppression.restore(MessagingTelemetryClaims.none());
  }

  @Test
  void nestedClaimsUnwindToWhereTheyStarted() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    MessagingTelemetryClaims beforeOuter = suppression.suppress(PROCESS, SPAN);
    MessagingTelemetryClaims beforeInner = suppression.suppress(RECEIVE, CONSUMED_MESSAGES);

    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(suppression.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isTrue();

    suppression.restore(beforeInner);
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(suppression.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isFalse();

    suppression.restore(beforeOuter);
    assertThat(suppression.current()).isEqualTo(MessagingTelemetryClaims.none());
  }

  @Test
  void repeatingAClaimStillUnwinds() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    MessagingTelemetryClaims beforeOuter = suppression.suppress(PROCESS, SPAN);
    MessagingTelemetryClaims beforeInner = suppression.suppress(PROCESS, SPAN);

    suppression.restore(beforeInner);
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();

    suppression.restore(beforeOuter);
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isFalse();
  }

  @Test
  void restoringInAFinallyBlockSurvivesAnException() {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();

    MessagingTelemetryClaims previous = suppression.suppress(PROCESS, SPAN);
    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                throw new IllegalStateException("boom");
              } finally {
                suppression.restore(previous);
              }
            });

    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isFalse();
  }

  @Test
  void oneThreadDoesNotSeeAnotherThreadsClaims() throws InterruptedException {
    MessagingTelemetrySuppression suppression = MessagingTelemetrySuppression.create();
    AtomicBoolean suppressedOnOtherThread = new AtomicBoolean(true);

    suppression.suppress(PROCESS, SPAN);
    Thread other =
        new Thread(() -> suppressedOnOtherThread.set(suppression.isSuppressed(PROCESS, SPAN)));
    other.start();
    other.join();

    assertThat(suppressedOnOtherThread).isFalse();
    assertThat(suppression.isSuppressed(PROCESS, SPAN)).isTrue();

    suppression.restore(MessagingTelemetryClaims.none());
  }

  @Test
  void oneStackDoesNotSeeAnotherStacksClaims() {
    MessagingTelemetrySuppression stack = MessagingTelemetrySuppression.create();
    MessagingTelemetrySuppression otherStack = MessagingTelemetrySuppression.create();

    stack.suppress(PROCESS, SPAN);

    assertThat(stack.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(otherStack.isSuppressed(PROCESS, SPAN)).isFalse();
    assertThat(otherStack.current()).isEqualTo(MessagingTelemetryClaims.none());

    stack.restore(MessagingTelemetryClaims.none());
  }
}
