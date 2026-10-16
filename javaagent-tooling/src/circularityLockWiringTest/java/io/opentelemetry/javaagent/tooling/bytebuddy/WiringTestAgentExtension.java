/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.agent.builder.AgentBuilder;

public final class WiringTestAgentExtension implements AgentExtension {

  // Keep these classes unloaded until the test explicitly triggers them.
  static final String TRIGGER_CLASS =
      "io.opentelemetry.javaagent.tooling.bytebuddy.WiringTestTriggerClass";
  static final String PROBE_CLASS =
      "io.opentelemetry.javaagent.tooling.bytebuddy.WiringTestProbeClass";

  static final AtomicBoolean lockHeldDuringTrigger = new AtomicBoolean();
  static final AtomicBoolean loggableDuringTrigger = new AtomicBoolean(true);
  static final AtomicBoolean probeTransformed = new AtomicBoolean();

  static InternalLogger logger;

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder, ConfigProperties config) {
    return agentBuilder
        .type(named(TRIGGER_CLASS))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) -> {
              lockHeldDuringTrigger.set(AgentCircularityLock.INSTANCE.isHeldByCurrentThread());
              loggableDuringTrigger.set(logger.isLoggable(INFO));
              logger.log(INFO, "during transformation", null);
              return builder;
            })
        .type(named(PROBE_CLASS))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) -> {
              probeTransformed.set(true);
              return builder;
            });
  }

  @Override
  public String extensionName() {
    return "application-logging-wiring-test";
  }
}
