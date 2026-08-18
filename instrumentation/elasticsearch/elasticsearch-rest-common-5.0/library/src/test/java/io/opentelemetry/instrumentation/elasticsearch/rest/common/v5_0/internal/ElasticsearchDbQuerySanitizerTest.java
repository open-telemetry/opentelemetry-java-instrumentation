/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ElasticsearchDbQuerySanitizerTest {

  @Test
  void masksStringValues() {
    assertThat(
            ElasticsearchDbQuerySanitizer.sanitize(
                "{\"query\":{\"match\":{\"title\":\"secret user data\"}}}"))
        .isEqualTo("{\"query\":{\"match\":{\"title\":\"?\"}}}");
  }

  @Test
  void masksAllScalarTypes() {
    assertThat(
            ElasticsearchDbQuerySanitizer.sanitize(
                "{\"a\":0,\"b\":-12.5e3,\"c\":true,\"d\":false,\"e\":null}"))
        .isEqualTo("{\"a\":\"?\",\"b\":\"?\",\"c\":\"?\",\"d\":\"?\",\"e\":\"?\"}");
  }

  @Test
  void masksArrayElements() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("[\"a\",1,true,null,{\"k\":\"v\"}]"))
        .isEqualTo("[\"?\",\"?\",\"?\",\"?\",{\"k\":\"?\"}]");
  }

  @Test
  void preservesKeysWithEscapesAndMasksEscapedStringValues() {
    // keys are kept verbatim (including escapes); string values are masked regardless of content
    assertThat(
            ElasticsearchDbQuerySanitizer.sanitize(
                "{\"a\\\"b\":\"he said \\\"hi\\\" and \\\\ done\"}"))
        .isEqualTo("{\"a\\\"b\":\"?\"}");
  }

  @Test
  void handlesUnicodeEscapesInStringValues() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"k\":\"\\u0041bc\"}"))
        .isEqualTo("{\"k\":\"?\"}");
  }

  @Test
  void ignoresInsignificantWhitespaceAndEmitsCompactOutput() {
    assertThat(
            ElasticsearchDbQuerySanitizer.sanitize(
                "  {\n  \"query\" : {\n    \"term\" : { \"id\" : 42 }\n  }\n}\n"))
        .isEqualTo("{\"query\":{\"term\":{\"id\":\"?\"}}}");
  }

  @Test
  void keepsEmptyObjectsAndArrays() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"a\":{},\"b\":[]}"))
        .isEqualTo("{\"a\":{},\"b\":[]}");
  }

  @Test
  void returnsNullForNonJson() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("this is not json")).isNull();
  }

  @Test
  void returnsNullForMalformedJson() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"query\":")).isNull();
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"query\":{}")).isNull();
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"a\":1,}")).isNull();
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("")).isNull();
  }

  @Test
  void returnsNullForTrailingContent() {
    assertThat(ElasticsearchDbQuerySanitizer.sanitize("{\"a\":1} extra")).isNull();
  }
}
