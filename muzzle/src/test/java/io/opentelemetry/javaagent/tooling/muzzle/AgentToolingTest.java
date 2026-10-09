/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.junit.jupiter.api.Test;

class AgentToolingTest {
  private final AgentBuilder.Listener listener = AgentTooling.transformListener();
  private final ClassLoader classLoader = getClass().getClassLoader();

  @Test
  void nestedTransformsRestoreOuterTransform() {
    ClassLoader innerClassLoader = new ClassLoader(classLoader) {};
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isTrue();
      listener.onDiscovery("Inner", innerClassLoader, null, false);
      try {
        assertThat(AgentTooling.isTransforming(innerClassLoader, "Inner")).isTrue();
        assertThat(AgentTooling.isTransforming(classLoader, "Inner")).isFalse();
        assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
      } finally {
        listener.onComplete("Inner", innerClassLoader, null, false);
      }
      assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isTrue();
      assertThat(AgentTooling.isTransforming(innerClassLoader, "Inner")).isFalse();
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
  }

  @Test
  void errorsDoNotPopUntilCompletion() {
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      listener.onDiscovery("Inner", classLoader, null, false);
      try {
        listener.onError("Inner", classLoader, null, false, new IllegalStateException());
        assertThat(AgentTooling.isTransforming(classLoader, "Inner")).isTrue();
        assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
      } finally {
        listener.onComplete("Inner", classLoader, null, false);
      }
      assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isTrue();
      listener.onError("Outer", classLoader, null, false, new IllegalStateException());
      assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isTrue();
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
    assertThat(AgentTooling.isTransforming(classLoader, "Inner")).isFalse();
  }

  @Test
  void bootstrapTransformsMaskAndRestoreOuterTransform() {
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      listener.onDiscovery("Bootstrap", null, null, false);
      try {
        assertThat(AgentTooling.isTransforming(null, "Bootstrap")).isTrue();
        assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
      } finally {
        listener.onComplete("Bootstrap", null, null, false);
      }
      assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isTrue();
      assertThat(AgentTooling.isTransforming(null, "Bootstrap")).isFalse();
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(AgentTooling.isTransforming(classLoader, "Outer")).isFalse();
  }
}
