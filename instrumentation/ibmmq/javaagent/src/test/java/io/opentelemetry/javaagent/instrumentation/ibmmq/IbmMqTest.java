/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Covers the opt-in default and the safety of the QMID helpers for the javax namespace. See {@code
 * IbmMqJakartaTest} (in the sibling {@code instrumentation:ibmmq:ibmmq-jakarta:javaagent} module,
 * which is where the jakarta client and API dependencies live) for the jakarta namespace
 * equivalent; it cannot live here because this module deliberately has no jakarta dependency.
 *
 * <p>Broker-backed integration tests are in {@code IbmMqJmsTest} (javax) and {@code
 * IbmMqJakartaJmsTest} (jakarta), run against testcontainers. They verify QMID enrichment on JMS
 * producer spans, asynchronous listener process spans, and the message-keyed fallback path.
 */
class IbmMqTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void attributeIsOptInAndOffUnlessEnabled() {
    // Runs under both the default test task (flag absent -> false) and testExperimental (flag set
    // -> true), so the opt_in default is asserted rather than assumed.
    assertThat(IbmMqQmidSupport.enabled())
        .isEqualTo(Boolean.getBoolean("otel.instrumentation.ibmmq.experimental-span-attributes"));
  }

  @Test
  void readQmidReturnsNullForNonIbmObject() {
    // Both the JMS and listener paths funnel through this, and it must never throw for a foreign
    // JMS provider or an unexpected argument.
    assertThat(IbmMqJmsQmid.readQmid(new Object())).isNull();
    assertThat(IbmMqJmsQmid.readQmid("not a consumer")).isNull();
  }

  @Test
  void enrichmentHelpersNeverThrow() {
    assertThatCode(
            () -> {
              IbmMqJmsQmid.stampMessagingSpan(new Object());
              IbmMqJmsListenerQmid.associate(new Object(), null);
              IbmMqJmsListenerQmid.stamp(null, null);
              IbmMqJmsListenerQmid.captureFromReceive(new Object(), null);
            })
        .doesNotThrowAnyException();
  }
}
