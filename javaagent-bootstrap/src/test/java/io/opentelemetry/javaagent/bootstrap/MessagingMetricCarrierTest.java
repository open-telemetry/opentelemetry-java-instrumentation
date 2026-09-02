/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static io.opentelemetry.javaagent.bootstrap.MessagingMetricCarrier.CONSUMED_MESSAGES;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagingMetricCarrierTest {

  @Test
  void storesAndCopiesClaims() {
    Object source = new Object();
    Object target = new Object();

    MessagingMetricCarrier.markClaim(source, CONSUMED_MESSAGES);
    MessagingMetricCarrier.copyClaims(source, target);

    assertThat(MessagingMetricCarrier.hasClaim(source, CONSUMED_MESSAGES)).isTrue();
    assertThat(MessagingMetricCarrier.hasClaim(target, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void clearsTargetClaimsWhenSourceHasNone() {
    Object target = new Object();
    MessagingMetricCarrier.markClaim(target, CONSUMED_MESSAGES);

    MessagingMetricCarrier.copyClaims(new Object(), target);

    assertThat(MessagingMetricCarrier.hasClaim(target, CONSUMED_MESSAGES)).isFalse();
  }
}
