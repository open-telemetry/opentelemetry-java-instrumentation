/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.junit.jupiter.api.Test;

class UnsafeAttributesTest {

  @Test
  void remove() {
    AttributesBuilder attributes = new UnsafeAttributes();
    attributes.put("animal", "cat").put("lives", 9);

    assertThat(attributes.remove(stringKey("animal"))).isSameAs(attributes);
    assertThat(attributes.build()).containsOnly(attributeEntry("lives", 9L));

    assertThat(attributes.remove(stringKey("missing"))).isSameAs(attributes);
    assertThat(attributes.build()).containsOnly(attributeEntry("lives", 9L));
  }

  @Test
  void buildAndUse() {
    Attributes previous =
        new UnsafeAttributes().put("world", "earth").put("country", "japan").build();

    UnsafeAttributes attributes = new UnsafeAttributes();
    attributes.put(stringKey("animal"), "cat");
    attributes.put("needs_catnip", false);
    // Overwrites
    attributes.put("needs_catnip", true);
    attributes.put(longKey("lives"), 9);
    attributes.putAll(previous);

    assertThat((Attributes) attributes)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "japan"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L));

    Attributes built = attributes.build();
    assertThat(built)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "japan"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L));

    attributes.put("clothes", "fur");
    assertThat((Attributes) attributes)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "japan"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L),
            attributeEntry("clothes", "fur"));

    // Unmodified
    assertThat(built)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "japan"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L));

    Attributes modified = attributes.toBuilder().put("country", "us").build();
    assertThat(modified)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "us"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L),
            attributeEntry("clothes", "fur"));

    // Unmodified
    assertThat((Attributes) attributes)
        .containsOnly(
            attributeEntry("world", "earth"),
            attributeEntry("country", "japan"),
            attributeEntry("animal", "cat"),
            attributeEntry("needs_catnip", true),
            attributeEntry("lives", 9L),
            attributeEntry("clothes", "fur"));
  }
}
