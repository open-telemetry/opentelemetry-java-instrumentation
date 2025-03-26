/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import org.junit.jupiter.api.Test;

class HostResourceTest {
  @Test
  void shouldCreateRuntimeAttributes() {
    // when
    Resource resource = HostResource.buildResource();
    Attributes attributes = resource.getAttributes();

    // then
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(attributes.get(HostIncubatingAttributes.HOST_NAME)).isNotBlank();
    assertThat(attributes.get(HostIncubatingAttributes.HOST_ARCH)).isNotBlank();
  }
}
