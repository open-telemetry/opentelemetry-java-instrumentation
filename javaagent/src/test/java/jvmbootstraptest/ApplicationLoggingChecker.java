/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.IntegrationTestUtils;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ApplicationLoggingChecker {

  private static final String VERSION_LOGGER = "io.opentelemetry.javaagent.tooling.VersionLogger";
  private static final String TRIGGER_CLASS =
      ApplicationLoggingChecker.class.getName() + "$Trigger";
  private static final String PROBE_CLASS = ApplicationLoggingChecker.class.getName() + "$Probe";

  public static void main(String[] args) throws Exception {
    AtomicInteger applicationCalls = new AtomicInteger();
    AtomicReference<Thread> loggedOn = new AtomicReference<>();
    AtomicBoolean loadProbe = new AtomicBoolean();
    ApplicationLoggerBridge.installApplicationLogger(
        name ->
            new InternalLogger() {
              @Override
              public boolean isLoggable(Level level) {
                if (name.equals(VERSION_LOGGER)) {
                  applicationCalls.incrementAndGet();
                }
                return level == Level.INFO;
              }

              @Override
              public void log(Level level, String message, Throwable error) {
                if (name.equals(VERSION_LOGGER)) {
                  applicationCalls.incrementAndGet();
                  loggedOn.set(Thread.currentThread());
                  if (loadProbe.get()) {
                    try {
                      Class.forName(PROBE_CLASS);
                    } catch (ClassNotFoundException e) {
                      throw new IllegalStateException(e);
                    }
                  }
                }
              }

              @Override
              public String name() {
                return name;
              }
            });
    assertThat(loggedOn.get()).isSameAs(Thread.currentThread());

    // The recording bridge must be installed before the test utility initializes its SLF4J logger.
    ClassLoader agentLoader = IntegrationTestUtils.getAgentClassLoader();
    Method logVersions = agentLoader.loadClass(VERSION_LOGGER).getMethod("logAllVersions");
    Class<?> lockClass =
        agentLoader.loadClass("io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock");
    Object lock = lockClass.getField("INSTANCE").get(null);
    Method isHeld = lockClass.getMethod("isHeldByCurrentThread");
    assertThat(isHeld.invoke(lock)).isEqualTo(false);
    logVersions.invoke(null);
    applicationCalls.set(0);
    loggedOn.set(null);

    AtomicBoolean lockHeldDuringTrigger = new AtomicBoolean();
    AtomicBoolean probeDiscovered = new AtomicBoolean();
    AtomicReference<ReflectiveOperationException> callbackFailure = new AtomicReference<>();
    Method registerCallback =
        agentLoader
            .loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller")
            .getMethod("registerClassLoadCallback", String.class, Runnable.class);
    registerCallback.invoke(
        null,
        TRIGGER_CLASS,
        (Runnable)
            () -> {
              try {
                lockHeldDuringTrigger.set((Boolean) isHeld.invoke(lock));
                logVersions.invoke(null);
              } catch (ReflectiveOperationException e) {
                callbackFailure.set(e);
              }
            });
    registerCallback.invoke(null, PROBE_CLASS, (Runnable) () -> probeDiscovered.set(true));

    loadProbe.set(true);
    Class.forName(TRIGGER_CLASS);

    assertThat(lockHeldDuringTrigger.get()).isTrue();
    assertThat(callbackFailure.get()).isNull();
    assertThat(applicationCalls.get()).isZero();
    assertThat(loggedOn.get()).isNull();
    assertThat(probeDiscovered.get()).isFalse();
    assertThat(isHeld.invoke(lock)).isEqualTo(false);

    logVersions.invoke(null);

    assertThat(applicationCalls.get()).isEqualTo(3);
    assertThat(loggedOn.get()).isSameAs(Thread.currentThread());
    assertThat(probeDiscovered.get()).isTrue();
  }

  @SuppressWarnings("UnusedNestedClass")
  private static final class Trigger {}

  @SuppressWarnings("UnusedNestedClass")
  private static final class Probe {}

  private ApplicationLoggingChecker() {}
}
