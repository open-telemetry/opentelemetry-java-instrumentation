/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.File;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.SetSystemProperty;

class ProcessResourceTest {

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.12")
  void notWindows() {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ResourceAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ResourceAttributes.PROCESS_EXECUTABLE_PATH))
        .contains(File.separatorChar + "java")
        .doesNotEndWith(".exe");
    assertThat(attributes.get(ResourceAttributes.PROCESS_COMMAND_LINE))
        .contains(attributes.get(ResourceAttributes.PROCESS_EXECUTABLE_PATH));
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Windows 10")
  void windows() {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ResourceAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ResourceAttributes.PROCESS_EXECUTABLE_PATH))
        .contains(File.separatorChar + "java")
        .endsWith(".exe");
    assertThat(attributes.get(ResourceAttributes.PROCESS_COMMAND_LINE))
        .contains(attributes.get(ResourceAttributes.PROCESS_EXECUTABLE_PATH));
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @ExtendWith(SecurityManagerExtension.class)
  @EnabledOnJre(
      value = {JRE.JAVA_8, JRE.JAVA_11, JRE.JAVA_16},
      disabledReason = "Java 17 deprecates security manager for removal")
  static class SecurityManagerEnabled {
    @Test
    void empty() {
      Attributes attributes = ProcessResource.buildResource().getAttributes();
      assertThat(attributes.asMap()).containsOnlyKeys(ResourceAttributes.PROCESS_PID);
    }
  }
}
