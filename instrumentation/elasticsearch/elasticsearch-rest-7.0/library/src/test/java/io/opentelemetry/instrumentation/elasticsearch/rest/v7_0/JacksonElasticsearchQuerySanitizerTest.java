/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JacksonElasticsearchQuerySanitizerTest {

  private final JacksonElasticsearchQuerySanitizer sanitizer =
      new JacksonElasticsearchQuerySanitizer();

  @Test
  void masksEveryScalarAndPreservesStructure() {
    assertThat(
            sanitizer.apply(
                "{\"query\":{\"bool\":{\"must\":[\"secret\",42,true,null]}},\"empty\":{}}"))
        .isEqualTo("{\"query\":{\"bool\":{\"must\":[\"?\",\"?\",\"?\",\"?\"]}},\"empty\":{}}");
  }

  @Test
  void sanitizesNdJsonSequence() {
    assertThat(
            sanitizer.apply(
                "{\"index\":\"private-index\"}\n"
                    + "{\"query\":{\"match\":{\"title\":\"secret\"}}}\n"))
        .isEqualTo("{\"index\":\"?\"};{\"query\":{\"match\":{\"title\":\"?\"}}}");
  }

  @Test
  void failsClosedForMalformedBody() {
    assertThat(sanitizer.apply("{\"query\":")).isNull();
    assertThat(sanitizer.apply("{\"a\":1} trailing")).isNull();
    assertThat(sanitizer.apply("")).isNull();
  }

  @Test
  void enforcesNestingDepth() {
    assertThat(
            sanitizer.apply(
                nestedArray(JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH, "\"secret\"")))
        .isEqualTo(nestedArray(JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH, "\"?\""));
    assertThat(
            sanitizer.apply(
                nestedArray(JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH + 1, "")))
        .isNull();
  }

  private static String nestedArray(int depth, String innermost) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      result.append('[');
    }
    result.append(innermost);
    for (int i = 0; i < depth; i++) {
      result.append(']');
    }
    return result.toString();
  }
}
