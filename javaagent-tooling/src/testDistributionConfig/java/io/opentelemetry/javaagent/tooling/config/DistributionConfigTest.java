/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DistributionConfigTest {

  @BeforeAll
  static void setUp() {
    // Initialize OpenTelemetry SDK to load declarative configuration
    OpenTelemetryInstaller.installOpenTelemetrySdk(DistributionConfigTest.class.getClassLoader());
  }

  @Test
  void testIndyDevelopmentProperty() {
    assertThat(AgentDistributionConfig.get().isIndyEnabled()).isTrue();
  }

  @Test
  void testForceSynchronousAgentListeners() {
    assertThat(AgentDistributionConfig.get().isForceSynchronousAgentListeners()).isFalse();
  }

  @Test
  void testExcludeClasses() {
    assertThat(AgentDistributionConfig.get().getExcludeClasses())
        .containsExactly("com.example.excluded.Class1", "com.example.excluded.Class2");
  }

  @Test
  void testExcludeClassLoaders() {
    assertThat(AgentDistributionConfig.get().getExcludeClassLoaders())
        .containsExactly("com.example.ExcludedClassLoader");
  }

  @Test
  void testInstrumentationDefaultEnabledByDefault() {
    assertThat(AgentDistributionConfig.get().isInstrumentationDefaultEnabled()).isTrue();
  }

  @Test
  void testInstrumentationEnabled() {
    assertThat(AgentDistributionConfig.get().getInstrumentation().getEnabled())
        .containsExactly("tomcat", "spring-webmvc");
  }

  @Test
  void testInstrumentationDisabled() {
    assertThat(AgentDistributionConfig.get().getInstrumentation().getDisabled()).isEmpty();
  }
}
