/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributesExtractorTest {

  static class TestAttributesExtractor
      extends AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    protected void onStart(AttributesBuilder attributes, Map<String, String> request) {
      set(attributes, AttributeKey.stringKey("animal"), request.get("animal"));
      set(attributes, AttributeKey.stringKey("country"), request.get("country"));
    }

    @Override
    protected void onEnd(
        AttributesBuilder attributes, Map<String, String> request, Map<String, String> response) {
      set(attributes, AttributeKey.stringKey("food"), response.get("food"));
      set(attributes, AttributeKey.stringKey("number"), request.get("number"));
    }
  }

  @Test
  void normal() {
    TestAttributesExtractor extractor = new TestAttributesExtractor();
    Map<String, String> request = new HashMap<>();
    request.put("animal", "cat");
    Map<String, String> response = new HashMap<>();
    response.put("food", "pizza");
    AttributesBuilder attributesBuilder = Attributes.builder();
    extractor.onStart(attributesBuilder, request);
    extractor.onEnd(attributesBuilder, request, response);
    assertThat(attributesBuilder.build())
        .containsOnly(attributeEntry("animal", "cat"), attributeEntry("food", "pizza"));
  }
}
