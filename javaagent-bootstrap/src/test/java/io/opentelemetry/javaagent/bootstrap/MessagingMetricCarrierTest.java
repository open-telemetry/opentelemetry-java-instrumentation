/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagingMetricCarrierTest {

  @Test
  void storesAndCopiesConsumedMessages() {
    Object source = new Object();
    Object target = new Object();

    MessagingMetricCarrier.markConsumedMessages(source);
    MessagingMetricCarrier.copyConsumedMessages(source, target);

    assertThat(MessagingMetricCarrier.hasConsumedMessages(source)).isTrue();
    assertThat(MessagingMetricCarrier.hasConsumedMessages(target)).isTrue();
  }

  @Test
  void clearsTargetWhenSourceHasNoConsumedMessages() {
    Object target = new Object();
    MessagingMetricCarrier.markConsumedMessages(target);

    MessagingMetricCarrier.copyConsumedMessages(new Object(), target);

    assertThat(MessagingMetricCarrier.hasConsumedMessages(target)).isFalse();
  }
}
