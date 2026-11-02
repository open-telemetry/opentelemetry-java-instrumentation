/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaverContainerTest {

  @Test
  void allowsAdditionalUnobservedRegistryDefinitions() {
    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("test.metric.expected", 1);
    metrics.put("test.metric.unused", 0);
    Map<String, Integer> attributes = new HashMap<>();
    attributes.put("test.attribute.expected", 1);
    attributes.put("test.attribute.unused", 0);

    new WeaverContainer.WeaverValidationResult(
            emptyList(), emptySet(), emptySet(), metrics, attributes)
        .checkRegisteredMetrics("test.", singletonList("test.metric.expected"), emptyList())
        .checkRegisteredAttributes("test.", singletonList("test.attribute.expected"), emptyList());
  }

  @Test
  void missingExpectedDefinitionsFail() {
    WeaverContainer.WeaverValidationResult result =
        new WeaverContainer.WeaverValidationResult(
            emptyList(), emptySet(), emptySet(), new HashMap<>(), new HashMap<>());

    assertThatThrownBy(
            () ->
                result.checkRegisteredMetrics(
                    "test.", singletonList("test.metric.expected"), emptyList()))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                result.checkRegisteredAttributes(
                    "test.", singletonList("test.attribute.expected"), emptyList()))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void missingRequiredObservationsFail() {
    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("test.metric.expected", 0);
    Map<String, Integer> attributes = new HashMap<>();
    attributes.put("test.attribute.expected", 0);
    WeaverContainer.WeaverValidationResult result =
        new WeaverContainer.WeaverValidationResult(
            emptyList(), emptySet(), emptySet(), metrics, attributes);

    assertThatThrownBy(
            () ->
                result.checkRegisteredMetrics(
                    "test.", singletonList("test.metric.expected"), emptyList()))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                result.checkRegisteredAttributes(
                    "test.", singletonList("test.attribute.expected"), emptyList()))
        .isInstanceOf(AssertionError.class);
  }
}
