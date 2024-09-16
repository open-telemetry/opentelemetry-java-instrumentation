/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SecurityManagerExtension.class)
@EnabledOnJre(
    value = {JRE.JAVA_8, JRE.JAVA_11},
    disabledReason = "Java 17 deprecates security manager for removal")
class SecurityManagerResourceTest {

  @Test
  void hostResourceTestEmpty() {
    Attributes attributes = HostResource.buildResource().getAttributes();
    assertThat(attributes.asMap()).containsOnlyKeys(HostIncubatingAttributes.HOST_NAME);
  }

  @Test
  void osResourceEmpty() {
    assertThat(OsResource.buildResource()).isEqualTo(Resource.empty());
  }

  @Test
  void processResourceEmpty() {
    Attributes attributes = ProcessResource.buildResource().getAttributes();
    assertThat(attributes.asMap()).containsOnlyKeys(ProcessIncubatingAttributes.PROCESS_PID);
  }

  @Test
  void processRuntimeResourceEmpty() {
    assertThat(ProcessRuntimeResource.buildResource()).isEqualTo(Resource.empty());
  }
}
