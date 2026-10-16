/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
import io.opentelemetry.javaagent.logging.application.ApplicationLoggingCustomizer;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CircularityLockWiringTest {

  @BeforeEach
  @AfterEach
  void resetGlobalOpenTelemetry() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void applicationLoggingDoesNotLoadClassesDuringTransformation() throws Exception {
    ByteBuddyAgent.install();
    AgentInstaller.installBytebuddyAgent(
        ByteBuddyAgent.getInstrumentation(), getClass().getClassLoader());
    assertThat(WiringTestAgentExtension.lockHeldDuringTrigger.get()).isFalse();
    assertThat(WiringTestAgentExtension.probeTransformed.get()).isFalse();
    new ApplicationLoggingCustomizer().init();

    ClassLoader loader = getClass().getClassLoader();
    AtomicInteger applicationCalls = new AtomicInteger();
    AtomicReference<Thread> loggedOn = new AtomicReference<>();
    ApplicationLoggerBridge.installApplicationLogger(
        name -> {
          applicationCalls.incrementAndGet();
          return new InternalLogger() {
            @Override
            public boolean isLoggable(Level level) {
              applicationCalls.incrementAndGet();
              return true;
            }

            @Override
            public void log(Level level, String message, Throwable error) {
              applicationCalls.incrementAndGet();
              loggedOn.set(Thread.currentThread());
              try {
                Class.forName(WiringTestAgentExtension.PROBE_CLASS, true, loader);
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
              }
            }

            @Override
            public String name() {
              return name;
            }
          };
        });
    WiringTestAgentExtension.logger = InternalLogger.getLogger("test");

    Class.forName(WiringTestAgentExtension.TRIGGER_CLASS, true, loader);

    assertThat(WiringTestAgentExtension.lockHeldDuringTrigger.get()).isTrue();
    assertThat(WiringTestAgentExtension.loggableDuringTrigger.get()).isFalse();
    assertThat(applicationCalls.get()).isZero();
    assertThat(WiringTestAgentExtension.probeTransformed.get()).isFalse();
    assertThat(AgentCircularityLock.INSTANCE.isHeldByCurrentThread()).isFalse();

    assertThat(WiringTestAgentExtension.logger.isLoggable(INFO)).isTrue();
    WiringTestAgentExtension.logger.log(INFO, "outside transformation", null);

    assertThat(applicationCalls.get()).isEqualTo(3);
    assertThat(loggedOn.get()).isSameAs(Thread.currentThread());
    assertThat(WiringTestAgentExtension.probeTransformed.get()).isTrue();
  }
}
