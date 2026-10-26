/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.testing.util.ResourceSchemaAssertions.assertResourceEntitySchemasMatchSdk;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_NAME;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.Entity;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;

class ProcessRuntimeResourceTest {
  @BeforeAll
  static void initializeResource() {
    ProcessRuntimeResource.get();
  }

  @Test
  void shouldCreateRuntimeAttributes() {
    // when
    Resource resource = ProcessRuntimeResource.buildResource();
    Attributes attributes = resource.getAttributes();

    // then
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_40_0);
    assertResourceEntitySchemasMatchSdk(resource);
    assertThat(attributes.get(PROCESS_RUNTIME_NAME)).isNotBlank();
    assertThat(attributes.get(PROCESS_RUNTIME_VERSION)).isNotBlank();
    assertThat(attributes.get(PROCESS_RUNTIME_DESCRIPTION)).isNotBlank();
    assertThat(EntityUtil.getEntities(resource))
        .containsExactly(
            Entity.builder(
                    "process.runtime",
                    Attributes.of(
                        PROCESS_RUNTIME_NAME, attributes.get(PROCESS_RUNTIME_NAME),
                        PROCESS_RUNTIME_VERSION, attributes.get(PROCESS_RUNTIME_VERSION)))
                .setDescription(
                    Attributes.of(
                        PROCESS_RUNTIME_DESCRIPTION, attributes.get(PROCESS_RUNTIME_DESCRIPTION)))
                .setSchemaUrl(SchemaUrls.V1_40_0)
                .build());
    assertThat(EntityUtil.getUnassociatedAttributes(resource)).isEqualTo(Attributes.empty());
  }

  @ParameterizedTest
  @MethodSource("missingIdentifiers")
  @SetSystemProperty(key = "java.runtime.name", value = "Java Runtime")
  @SetSystemProperty(key = "java.runtime.version", value = "1.0")
  void shouldNotCreateEntityWithMissingIdentifiers(String name, String version) {
    if (name == null) {
      System.clearProperty("java.runtime.name");
    }
    if (version == null) {
      System.clearProperty("java.runtime.version");
    }

    Resource resource = ProcessRuntimeResource.buildResource();
    Attributes attributes = resource.getAttributes();

    assertThat(EntityUtil.getEntities(resource)).isEmpty();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_40_0);
    assertThat(attributes.get(PROCESS_RUNTIME_NAME)).isEqualTo(name);
    assertThat(attributes.get(PROCESS_RUNTIME_VERSION)).isEqualTo(version);
    assertThat(attributes.get(PROCESS_RUNTIME_DESCRIPTION)).isNotBlank();
    assertThat(EntityUtil.getUnassociatedAttributes(resource)).isEqualTo(attributes);
  }

  private static Stream<Arguments> missingIdentifiers() {
    return Stream.of(
        argumentSet("missing name", null, "1.0"),
        argumentSet("missing version", "Java Runtime", null),
        argumentSet("missing name and version", null, null));
  }
}
