/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.bytebuddy.agent.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AgentBuilderUtilTest {
  private final AgentBuilder.Listener listener;
  private final Method getTransformedClassName;
  private final ClassLoader classLoader = getClass().getClassLoader();

  AgentBuilderUtilTest() throws Exception {
    AgentBuilder agentBuilder = AgentBuilderUtil.optimize(new AgentBuilder.Default());
    Field listenerField = AgentBuilder.Default.class.getDeclaredField("listener");
    listenerField.setAccessible(true);
    listener = (AgentBuilder.Listener) listenerField.get(agentBuilder);
    Class<?> transformContext =
        Class.forName(AgentBuilderUtil.class.getName() + "$TransformContext");
    getTransformedClassName = transformContext.getDeclaredMethod("getTransformedClassName");
    getTransformedClassName.setAccessible(true);
  }

  @Test
  void nestedTransformsRestoreOuterName() throws Exception {
    assertThat(transformedClassName()).isNull();
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      assertThat(transformedClassName()).isEqualTo("Outer");
      listener.onDiscovery("Inner", classLoader, null, false);
      try {
        assertThat(transformedClassName()).isEqualTo("Inner");
      } finally {
        listener.onComplete("Inner", classLoader, null, false);
      }
      assertThat(transformedClassName()).isEqualTo("Outer");
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(transformedClassName()).isNull();
  }

  @Test
  void errorsDoNotPopUntilCompletion() throws Exception {
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      listener.onDiscovery("Inner", classLoader, null, false);
      try {
        listener.onError("Inner", classLoader, null, false, new IllegalStateException());
        assertThat(transformedClassName()).isEqualTo("Inner");
      } finally {
        listener.onComplete("Inner", classLoader, null, false);
      }
      assertThat(transformedClassName()).isEqualTo("Outer");
      listener.onError("Outer", classLoader, null, false, new IllegalStateException());
      assertThat(transformedClassName()).isEqualTo("Outer");
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(transformedClassName()).isNull();
  }

  @Test
  void bootstrapTransformsMaskAndRestoreOuterName() throws Exception {
    listener.onDiscovery("Outer", classLoader, null, false);
    try {
      listener.onDiscovery("Bootstrap", null, null, false);
      try {
        assertThat(transformedClassName()).isNull();
      } finally {
        listener.onComplete("Bootstrap", null, null, false);
      }
      assertThat(transformedClassName()).isEqualTo("Outer");
    } finally {
      listener.onComplete("Outer", classLoader, null, false);
    }
    assertThat(transformedClassName()).isNull();
  }

  private String transformedClassName() throws Exception {
    return (String) getTransformedClassName.invoke(null);
  }
}
