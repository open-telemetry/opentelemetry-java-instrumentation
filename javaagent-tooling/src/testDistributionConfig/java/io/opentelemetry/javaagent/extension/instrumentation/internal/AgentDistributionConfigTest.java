/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AgentDistributionConfigTest {

  @BeforeAll
  static void setUp() {
    // Initialize OpenTelemetry SDK to load declarative configuration
    OpenTelemetryInstaller.installOpenTelemetrySdk(
        AgentDistributionConfigTest.class.getClassLoader());
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
    AgentDistributionConfig config = AgentDistributionConfig.get();
    assertThat(config.isInstrumentationEnabled("tomcat", false)).isTrue();
    assertThat(config.isInstrumentationEnabled("spring_webmvc", false)).isTrue();
    assertThat(config.isInstrumentationEnabled("unknown", false)).isFalse();
  }

  @Test
  void testInstrumentationDisabled() {
    // An unknown instrumentation with defaultEnabled=true should be enabled
    AgentDistributionConfig config = AgentDistributionConfig.get();
    assertThat(config.isInstrumentationEnabled("unknown", true)).isTrue();
  }

  @Test
  void testInstrumentationEnabledOrderMatters() {
    // armeria is enabled, armeria_grpc is disabled
    // first matching name wins (matches ConfigProperties behavior)
    AgentDistributionConfig config = AgentDistributionConfig.get();

    // armeria-grpc listed first: disabled wins
    assertThat(
            config.isInstrumentationEnabled(
                Arrays.asList("armeria-grpc", "armeria-grpc-1.14", "armeria", "armeria-1.14"),
                true))
        .isFalse();

    // armeria listed first: enabled wins
    assertThat(
            config.isInstrumentationEnabled(
                Arrays.asList("armeria", "armeria-1.14", "armeria-grpc", "armeria-grpc-1.14"),
                true))
        .isTrue();

    // armeria alone should be enabled
    assertThat(
            config.isInstrumentationEnabled(
                Arrays.asList("armeria", "armeria-1.14"), true))
        .isTrue();
  }
}
