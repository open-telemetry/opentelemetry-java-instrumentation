/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_NAME;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_RUNTIME_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.Test;

class ProcessRuntimeResourceTest {
  @Test
  void shouldCreateRuntimeAttributes() {
    // when
    Resource resource = ProcessRuntimeResource.buildResource();
    Attributes attributes = resource.getAttributes();

    // then
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(attributes.get(PROCESS_RUNTIME_NAME)).isNotBlank();
    assertThat(attributes.get(PROCESS_RUNTIME_VERSION)).isNotBlank();
    assertThat(attributes.get(PROCESS_RUNTIME_DESCRIPTION)).isNotBlank();
  }
}
