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
 * Jakarta namespace counterpart of {@code IbmMqTest} (in the sibling {@code
 * instrumentation:ibmmq:javaagent} module's default test source set). Lives in this separate module
 * because that is where the jakarta client and API dependencies live -- see this module's {@code
 * build.gradle.kts} and {@code IbmMqJakartaInstrumentationModule}'s javadoc for why.
 */
class IbmMqJakartaTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void attributeIsOptInAndOffUnlessEnabled() {
    assertThat(IbmMqQmidSupport.enabled())
        .isEqualTo(Boolean.getBoolean("otel.instrumentation.ibmmq.experimental-span-attributes"));
  }

  @Test
  void readQmidReturnsNullForNonIbmObject() {
    assertThat(IbmMqJakartaJmsQmid.readQmid(new Object())).isNull();
    assertThat(IbmMqJakartaJmsQmid.readQmid("not a consumer")).isNull();
  }

  @Test
  void enrichmentHelpersNeverThrow() {
    assertThatCode(
            () -> {
              IbmMqJakartaJmsQmid.stampMessagingSpan(new Object());
              IbmMqJakartaJmsListenerQmid.associate(new Object(), null);
              IbmMqJakartaJmsListenerQmid.stamp(null);
              IbmMqJakartaJmsListenerQmid.stamp(null, null);
              IbmMqJakartaJmsListenerQmid.captureFromReceive(new Object(), null);
            })
        .doesNotThrowAnyException();
  }
}
