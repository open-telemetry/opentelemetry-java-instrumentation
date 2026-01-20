/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.emptyList;
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
  void testBooleanProperty() {
    assertThat(AgentDistributionConfig.get().getBoolean("indy/development", false)).isTrue();
  }

  @Test
  void testScalarListProperty() {
    assertThat(
            AgentDistributionConfig.get()
                .getScalarList("exclude_classes", String.class, emptyList()))
        .containsExactly("com.example.excluded.Class1", "com.example.excluded.Class2");
  }

  @Test
  void testClassLoadersList() {
    assertThat(
            AgentDistributionConfig.get()
                .getScalarList("exclude_class_loaders", String.class, emptyList()))
        .containsExactly("com.example.ExcludedClassLoader");
  }

  @Test
  void testAdditionalLibraryIgnores() {
    assertThat(
            AgentDistributionConfig.get()
                .getScalarList("additional_library_ignores", String.class, emptyList()))
        .containsExactly("com.example.ignored.Library");
  }

  @Test
  void testNestedStructuredProperty() {
    assertThat(
            AgentDistributionConfig.get()
                .getStructured("instrumentation")
                .getBoolean("default_enabled"))
        .isFalse();
  }

  @Test
  void testNestedListProperty() {
    assertThat(
            AgentDistributionConfig.get()
                .getStructured("instrumentation")
                .getScalarList("enabled", String.class, emptyList()))
        .containsExactly("tomcat", "spring-webmvc");
  }

  @Test
  void testNonExistentProperty() {
    assertThat(AgentDistributionConfig.get().getBoolean("non_existent_property", true)).isTrue();
  }

  @Test
  void testNonExistentListProperty() {
    assertThat(
            AgentDistributionConfig.get()
                .getScalarList("non_existent_list", String.class, emptyList()))
        .isEmpty();
  }
}
