/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Registered via {@code META-INF/services} for {@link TransformationTrackingWiringTest} only - see
 * that class for what this exercises.
 *
 * <p>The class name strings below are deliberately hardcoded rather than derived from {@code
 * WiringTestTriggerClass.class.getName()} - referencing the class literal here would load it as
 * soon as this extension class is initialized (during agent installation), long before the test is
 * ready to trigger it under controlled conditions.
 */
public final class WiringTestAgentExtension implements AgentExtension {

  static final String TRIGGER_CLASS =
      "io.opentelemetry.javaagent.tooling.bytebuddy.WiringTestTriggerClass";
  static final String PROBE_CLASS =
      "io.opentelemetry.javaagent.tooling.bytebuddy.WiringTestProbeClass";

  static final AtomicBoolean inTransformationDuringTrigger = new AtomicBoolean();
  static final AtomicBoolean probeTransformed = new AtomicBoolean();
  static final CountDownLatch probeLoaded = new CountDownLatch(1);

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder, ConfigProperties config) {
    return agentBuilder
        .type(named(TRIGGER_CLASS))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) -> {
              onTriggerTransform(classLoader);
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
    return "transformation-tracking-wiring-test";
  }

  private static void onTriggerTransform(ClassLoader classLoader) {
    boolean inTransformation = InTransformation.get();
    inTransformationDuringTrigger.set(inTransformation);

    Runnable loadProbe =
        () -> {
          try {
            Class.forName(PROBE_CLASS, true, classLoader);
          } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
          } finally {
            probeLoaded.countDown();
          }
        };

    if (inTransformation) {
      // mirrors TransformSafeApplicationLoggerFactory: a class first loaded from a bridged agent
      // log while a transformation is in progress must not load on this (transforming) thread,
      // where Byte Buddy's own circularity lock would silently skip instrumenting it
      Thread thread = new Thread(loadProbe);
      thread.setDaemon(true);
      thread.start();
    } else {
      loadProbe.run();
    }
  }
}
