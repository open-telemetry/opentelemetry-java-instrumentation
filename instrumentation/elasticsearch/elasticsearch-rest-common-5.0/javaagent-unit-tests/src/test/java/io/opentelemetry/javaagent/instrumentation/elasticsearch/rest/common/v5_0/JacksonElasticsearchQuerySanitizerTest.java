/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JacksonElasticsearchQuerySanitizerTest {

  private final JacksonElasticsearchQuerySanitizer sanitizer =
      new JacksonElasticsearchQuerySanitizer();

  private String sanitize(String body) {
    return sanitizer.apply(body);
  }

  @Test
  void masksStringValues() {
    assertThat(sanitize("{\"query\":{\"match\":{\"title\":\"secret user data\"}}}"))
        .isEqualTo("{\"query\":{\"match\":{\"title\":\"?\"}}}");
  }

  @Test
  void masksAllScalarTypes() {
    assertThat(sanitize("{\"a\":0,\"b\":-12.5e3,\"c\":true,\"d\":false,\"e\":null}"))
        .isEqualTo("{\"a\":\"?\",\"b\":\"?\",\"c\":\"?\",\"d\":\"?\",\"e\":\"?\"}");
  }

  @Test
  void masksArrayElements() {
    assertThat(sanitize("[\"a\",1,true,null,{\"k\":\"v\"}]"))
        .isEqualTo("[\"?\",\"?\",\"?\",\"?\",{\"k\":\"?\"}]");
  }

  @Test
  void masksTopLevelScalar() {
    assertThat(sanitize("\"secret\"")).isEqualTo("\"?\"");
  }

  @Test
  void preservesKeysWithEscapesAndMasksEscapedStringValues() {
    // keys are kept (including escapes); string values are masked regardless of content
    assertThat(sanitize("{\"a\\\"b\":\"he said \\\"hi\\\" and \\\\ done\"}"))
        .isEqualTo("{\"a\\\"b\":\"?\"}");
  }

  @Test
  void handlesUnicodeEscapesInStringValues() {
    assertThat(sanitize("{\"k\":\"\\u0041bc\"}")).isEqualTo("{\"k\":\"?\"}");
  }

  @Test
  void ignoresInsignificantWhitespaceAndEmitsCompactOutput() {
    assertThat(sanitize("  {\n  \"query\" : {\n    \"term\" : { \"id\" : 42 }\n  }\n}\n"))
        .isEqualTo("{\"query\":{\"term\":{\"id\":\"?\"}}}");
  }

  @Test
  void keepsEmptyObjectsAndArrays() {
    assertThat(sanitize("{\"a\":{},\"b\":[]}")).isEqualTo("{\"a\":{},\"b\":[]}");
  }

  @Test
  void sanitizesNdJsonSequence() {
    assertThat(
            sanitize(
                "{\"index\":\"private-index\"}\n"
                    + "{\"query\":{\"match\":{\"title\":\"secret\"}}}\n"
                    + "{}\n"
                    + "{\"id\":\"private-template\"}\n"))
        .isEqualTo("{\"index\":\"?\"};{\"query\":{\"match\":{\"title\":\"?\"}}};{};{\"id\":\"?\"}");
  }

  @Test
  void sanitizesSequenceWithoutSeparatingWhitespace() {
    // the caller strips newlines while reading the body, so multi-search values arrive back to back
    assertThat(
            sanitize("{\"index\":\"private-index\"}{\"query\":{\"match\":{\"title\":\"secret\"}}}"))
        .isEqualTo("{\"index\":\"?\"};{\"query\":{\"match\":{\"title\":\"?\"}}}");
  }

  @Test
  void returnsNullForNonJson() {
    assertThat(sanitize("this is not json")).isNull();
  }

  @Test
  void returnsNullForMalformedJson() {
    assertThat(sanitize("{\"query\":")).isNull();
    assertThat(sanitize("{\"query\":{}")).isNull();
    assertThat(sanitize("{\"a\":1,}")).isNull();
    assertThat(sanitize("")).isNull();
    assertThat(sanitize("   ")).isNull();
  }

  @Test
  void returnsNullForTrailingContent() {
    assertThat(sanitize("{\"a\":1} extra")).isNull();
  }

  @Test
  void returnsNullWhenAnyValueInSequenceIsMalformed() {
    assertThat(sanitize("{\"a\":1}\n{\"b\":")).isNull();
  }

  @Test
  void returnsNullWhenNestedTooDeeply() {
    // a body nested past the depth cap is valid JSON but must be dropped, not captured raw and not
    // partially masked
    int depth = JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH + 1;

    assertThat(sanitize(nestedArray(depth, ""))).isNull();
  }

  @Test
  void masksBodyNestedUpToTheDepthCap() {
    // a body nested exactly to the cap is still sanitized
    int depth = JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH;

    assertThat(sanitize(nestedArray(depth, "\"secret\""))).isEqualTo(nestedArray(depth, "\"?\""));
  }

  @Test
  void limitsSanitizedQueryLength() {
    String exactLimit =
        objectWithEmptyArrayField(JacksonElasticsearchQuerySanitizer.MAX_QUERY_LENGTH - 7);
    String overLimit =
        objectWithEmptyArrayField(JacksonElasticsearchQuerySanitizer.MAX_QUERY_LENGTH - 6);

    assertThat(sanitize(exactLimit)).isEqualTo(exactLimit);
    assertThat(sanitize(overLimit))
        .isEqualTo(overLimit.substring(0, JacksonElasticsearchQuerySanitizer.MAX_QUERY_LENGTH));
    assertThat(sanitize(overLimit + " trailing"))
        .isEqualTo(overLimit.substring(0, JacksonElasticsearchQuerySanitizer.MAX_QUERY_LENGTH));
  }

  @Test
  void stopsBeforeOverDepthContentAfterLimit() {
    String exactLimit =
        objectWithEmptyArrayField(JacksonElasticsearchQuerySanitizer.MAX_QUERY_LENGTH - 7);

    assertThat(
            sanitize(
                exactLimit
                    + nestedArray(JacksonElasticsearchQuerySanitizer.MAX_NESTING_DEPTH + 1, "")))
        .isEqualTo(exactLimit);
  }

  @Test
  void outputIsValidJsonThatKeepsKeysAndDropsEveryLiteral() throws IOException {
    // property-style check against a real parser rather than a hand-written expectation
    String body =
        "{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"topsecret\"}},"
            + "{\"range\":{\"age\":{\"gte\":4711,\"lte\":4712.5}}},"
            + "{\"term\":{\"live\":true}},{\"term\":{\"missing\":null}}]}},"
            + "\"_source\":[\"title\",\"age\"],\"from\":17,\"size\":23}";

    String sanitized = sanitize(body);

    assertThat(sanitized).isNotNull();
    assertThat(fieldNames(sanitized)).isEqualTo(fieldNames(body));
    assertThat(scalarValues(sanitized)).isNotEmpty().containsOnly("?");
    // none of the literals in the input survive anywhere in the output
    assertThat(sanitized).doesNotContain("topsecret", "4711", "4712.5", "true", "null", "17", "23");
  }

  @Test
  void keepsFieldNamesEvenWhenTheyLookLikeValues() throws IOException {
    String body = "{\"topsecret\":\"topsecret\"}";

    String sanitized = sanitize(body);

    assertThat(sanitized).isEqualTo("{\"topsecret\":\"?\"}");
    assertThat(fieldNames(sanitized)).containsExactly("topsecret");
  }

  private static List<String> fieldNames(String json) throws IOException {
    List<String> names = new ArrayList<>();
    forEachToken(json, (parser, token) -> names.add(parser.currentName()), JsonToken.FIELD_NAME);
    return names;
  }

  private static List<String> scalarValues(String json) throws IOException {
    List<String> values = new ArrayList<>();
    forEachToken(json, (parser, token) -> values.add(parser.getText()), JsonToken.VALUE_STRING);
    return values;
  }

  private static void forEachToken(String json, TokenVisitor visitor, JsonToken wanted)
      throws IOException {
    JsonFactory jsonFactory = new JsonFactory();
    try (JsonParser parser = jsonFactory.createParser(json)) {
      JsonToken token;
      while ((token = parser.nextToken()) != null) {
        if (token == wanted) {
          visitor.visit(parser, token);
        }
      }
    }
  }

  @FunctionalInterface
  private interface TokenVisitor {
    void visit(JsonParser parser, JsonToken token) throws IOException;
  }

  private static String nestedArray(int depth, String innermost) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append('[');
    }
    sb.append(innermost);
    for (int i = 0; i < depth; i++) {
      sb.append(']');
    }
    return sb.toString();
  }

  private static String objectWithEmptyArrayField(int fieldNameLength) {
    StringBuilder body = new StringBuilder(fieldNameLength + 7);
    body.append("{\"");
    for (int i = 0; i < fieldNameLength; i++) {
      body.append('a');
    }
    return body.append("\":[]}").toString();
  }
}
