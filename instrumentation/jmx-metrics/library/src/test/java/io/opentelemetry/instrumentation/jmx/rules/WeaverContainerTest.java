/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaverContainerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void checkRegisteredMetricsAllowsUnobservedExtraDefinitions() {
    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("jvm.metric.required", 1);
    metrics.put("jvm.metric.optional", 0);
    metrics.put("jvm.metric.unused", 0);
    metrics.put("other.metric", 1);

    result(metrics, emptyMap())
        .checkRegisteredMetrics(
            "jvm.", singletonList("jvm.metric.required"), singletonList("jvm.metric.optional"));
  }

  @Test
  void checkRegisteredMetricsRejectsObservedExtraDefinitions() {
    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("jvm.metric.unexpected", 1);

    assertThatThrownBy(
            () ->
                result(metrics, emptyMap())
                    .checkRegisteredMetrics("jvm.", emptyList(), emptyList()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("zero count for key jvm.metric.unexpected");
  }

  @Test
  void checkRegisteredAttributesAllowsUnobservedExtraDefinitions() {
    Map<String, Integer> attributes = new HashMap<>();
    attributes.put("jvm.attribute.required", 1);
    attributes.put("jvm.attribute.optional", 2);
    attributes.put("jvm.attribute.unused", 0);
    attributes.put("other.attribute", 1);

    result(emptyMap(), attributes)
        .checkRegisteredAttributes(
            "jvm.",
            singletonList("jvm.attribute.required"),
            singletonList("jvm.attribute.optional"));
  }

  @Test
  void checkRegisteredAttributesRejectsObservedExtraDefinitions() {
    Map<String, Integer> attributes = new HashMap<>();
    attributes.put("jvm.attribute.unexpected", 1);

    assertThatThrownBy(
            () ->
                result(emptyMap(), attributes)
                    .checkRegisteredAttributes("jvm.", emptyList(), emptyList()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("zero count for key jvm.attribute.unexpected");
  }

  @Test
  void checkRegisteredKeepsRequiredAndOptionalCountChecks() {
    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("jvm.metric.required", 0);
    metrics.put("jvm.metric.optional", 0);

    assertThatThrownBy(
            () ->
                result(metrics, emptyMap())
                    .checkRegisteredMetrics(
                        "jvm.",
                        singletonList("jvm.metric.required"),
                        singletonList("jvm.metric.optional")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("count > 0");

    result(metrics, emptyMap())
        .checkRegisteredMetrics("jvm.", emptyList(), singletonList("jvm.metric.optional"));
  }

  @Test
  void checkRegisteredStillRequiresExpectedDefinitions() {
    assertThatThrownBy(
            () ->
                result(emptyMap(), emptyMap())
                    .checkRegisteredAttributes(
                        "jvm.", singletonList("jvm.attribute.required"), emptyList()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("contain all keys");
  }

  private static WeaverContainer.WeaverValidationResult result(
      Map<String, Integer> metrics, Map<String, Integer> attributes) {
    ObjectNode json = OBJECT_MAPPER.createObjectNode();
    json.putArray("samples");
    ObjectNode statistics = json.putObject("statistics");
    statistics.set("seen_non_registry_metrics", OBJECT_MAPPER.valueToTree(emptyMap()));
    statistics.set("seen_non_registry_attributes", OBJECT_MAPPER.valueToTree(emptyMap()));
    statistics.set("seen_registry_metrics", OBJECT_MAPPER.valueToTree(metrics));
    statistics.set("seen_registry_attributes", OBJECT_MAPPER.valueToTree(attributes));
    return WeaverContainer.WeaverValidationResult.fromJson(json);
  }
}
