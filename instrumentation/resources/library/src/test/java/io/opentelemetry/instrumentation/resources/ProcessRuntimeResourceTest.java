/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

class ProcessRuntimeResourceTest {
  @Test
  void shouldCreateRuntimeAttributes() {
    // when
    Resource resource = ProcessRuntimeResource.buildResource();
    Attributes attributes = resource.getAttributes();

    // then
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(attributes.get(ResourceAttributes.PROCESS_RUNTIME_NAME)).isNotBlank();
    assertThat(attributes.get(ResourceAttributes.PROCESS_RUNTIME_VERSION)).isNotBlank();
    assertThat(attributes.get(ResourceAttributes.PROCESS_RUNTIME_DESCRIPTION)).isNotBlank();
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
      assertThat(ProcessRuntimeResource.buildResource()).isEqualTo(Resource.empty());
    }
  }
}
