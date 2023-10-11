/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

class ConfiguredResourceAttributesHolderTest {

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.mdc.resource-attributes",
      value = "service.name,runtime")
  void testGetAttributeValue() {
    Attributes attributes =
        Attributes.builder().put("service.name", "test-service").put("runtime", "JVM").build();

    ConfiguredResourceAttributesHolder.initialize(attributes);

    assertThat(ConfiguredResourceAttributesHolder.getAttributeValue("service.name"))
        .isEqualTo("test-service");
    assertThat(ConfiguredResourceAttributesHolder.getAttributeValue("runtime")).isEqualTo("JVM");
  }

  @Test
  @SetSystemProperty(key = "otel.instrumentation.mdc.resource-attributes", value = "items")
  void testGetAttributeValueWhenKeyIsNotString() {
    Attributes attributes =
        Attributes.builder()
            .put(AttributeKey.stringArrayKey("items"), Collections.singletonList("test-item"))
            .build();

    ConfiguredResourceAttributesHolder.initialize(attributes);
    assertThat(ConfiguredResourceAttributesHolder.getAttributeValue("items")).isNull();
  }

  @Test
  @ClearSystemProperty(key = "otel.instrumentation.mdc.resource-attributes")
  void testGetAttributeValueWhenConfigIsNotSet() {
    Attributes attributes =
        Attributes.builder().put(AttributeKey.stringArrayKey("don't care"), "won't care").build();

    ConfiguredResourceAttributesHolder.initialize(attributes);
    assertThat(ConfiguredResourceAttributesHolder.getAttributeValue("dc-wc")).isNull();
  }
}
