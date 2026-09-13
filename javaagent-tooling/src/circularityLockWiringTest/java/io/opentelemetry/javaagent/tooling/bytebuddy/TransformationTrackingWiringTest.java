/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Installs the real agent in-process (same pattern as {@code
 * HelperInjectionTest#helpersInjectedOnBootstrapClassloader}) and exercises the causal path the fix
 * depends on:
 *
 * <ol>
 *   <li>{@link TransformationTrackingCircularityLock}, wired into {@code
 *       AgentInstaller.newAgentBuilder}, is what makes {@link
 *       io.opentelemetry.javaagent.bootstrap.internal.InTransformation#get()} observable during a
 *       real Byte Buddy transformation.
 *   <li>That flag is what lets a bridged agent log (here, {@link WiringTestAgentExtension})
 *       recognize it must defer a first class load to a separate thread, instead of letting it
 *       happen reentrantly on the transforming thread - where Byte Buddy's own circularity lock
 *       would otherwise silently skip instrumenting it, forever.
 * </ol>
 *
 * <p>This test must run alone in its own JVM - see the {@code circularityLockWiringTest} task in
 * this module's build.gradle.kts. A second, independent agent installation in the same process
 * would bring its own, never-yet-acquired circularity lock, which would happily "transform" the
 * probe class when loaded reentrantly, masking the very bug this test exists to catch.
 */
class TransformationTrackingWiringTest {

  @BeforeEach
  @AfterEach
  void resetGlobalOpenTelemetry() {
    // installBytebuddyAgent() sets the global OpenTelemetry, which can only be set once per JVM
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void aClassFirstLoadedFromABridgedLogDuringATransformationStillGetsTransformed()
      throws Exception {
    ByteBuddyAgent.install();
    AgentInstaller.installBytebuddyAgent(
        ByteBuddyAgent.getInstrumentation(), getClass().getClassLoader());

    // loading the trigger class is itself a real, first-time Byte Buddy transformation;
    // WiringTestAgentExtension observes InTransformation.get() from inside it and defers loading
    // the probe class accordingly
    Class.forName(WiringTestAgentExtension.TRIGGER_CLASS, true, getClass().getClassLoader());

    assertThat(WiringTestAgentExtension.inTransformationDuringTrigger.get())
        .withFailMessage(
            "InTransformation.get() was false while the trigger class was being transformed")
        .isTrue();

    assertThat(WiringTestAgentExtension.probeLoaded.await(10, SECONDS))
        .withFailMessage("the probe class was never loaded")
        .isTrue();
    assertThat(WiringTestAgentExtension.probeTransformed.get())
        .withFailMessage(
            "the probe class, first loaded from inside the trigger's transformation, was not"
                + " transformed - it was silently skipped by the circularity lock instead")
        .isTrue();
  }
}
