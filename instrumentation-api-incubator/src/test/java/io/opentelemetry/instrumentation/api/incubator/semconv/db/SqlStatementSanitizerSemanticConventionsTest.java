/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SqlStatementSanitizerSemanticConventionsTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  static List<TestCase> loadTestCases() throws IOException {
    List<TestCase> testCases = new ArrayList<>();
    try (InputStream input =
        SqlStatementSanitizerSemanticConventionsTest.class.getResourceAsStream(
            "/db-sql-test-cases.json")) {
      if (input == null) {
        throw new IOException("Could not find db-sql-test-cases.json in resources");
      }
      JsonNode rootNode = mapper.readTree(input);
      for (JsonNode testNode : rootNode) {
        String name = testNode.get("name").asText();
        JsonNode inputNode = testNode.get("input");
        String query = inputNode.get("query").asText();

        JsonNode expectedNode = testNode.get("expected");
        List<String> expectedQueryTexts = new ArrayList<>();
        JsonNode queryTextNode = expectedNode.get("db.query.text");
        if (queryTextNode.isArray()) {
          for (JsonNode textNode : queryTextNode) {
            expectedQueryTexts.add(textNode.asText());
          }
        }

        String expectedQuerySummary = null;
        if (expectedNode.has("db.query.summary")) {
          expectedQuerySummary = expectedNode.get("db.query.summary").asText();
        }

        testCases.add(new TestCase(name, query, expectedQueryTexts, expectedQuerySummary));
      }
    }
    return testCases;
  }

  @ParameterizedTest
  @MethodSource("loadTestCases")
  void testSemanticConventions(TestCase testCase) {
    SqlStatementInfo result = SqlStatementSanitizer.create(true).sanitize(testCase.query);

    // Check if the actual query text matches any of the expected alternatives
    // Normalize whitespace for comparison (semantic conventions test for other aspects)
    boolean matchesExpectedQueryText = false;
    if (result.getQueryText() != null) {
      String actualNormalized = normalizeWhitespace(result.getQueryText());
      for (String expectedQueryText : testCase.expectedQueryTexts) {
        String expectedNormalized = normalizeWhitespace(expectedQueryText);
        if (actualNormalized.equals(expectedNormalized)) {
          matchesExpectedQueryText = true;
          break;
        }
      }
    }

    assertThat(matchesExpectedQueryText)
        .as(
            "Test case: %s\nExpected one of: %s\nActual: %s",
            testCase.name, testCase.expectedQueryTexts, result.getQueryText())
        .isTrue();

    assertThat(result.getQuerySummary())
        .as("Test case: %s - query summary", testCase.name)
        .isEqualTo(testCase.expectedQuerySummary);
  }

  private static String normalizeWhitespace(String text) {
    if (text == null) {
      return null;
    }
    // Replace all sequences of whitespace (including newlines) with a single space
    // and trim leading/trailing whitespace
    return text.replaceAll("\\s+", " ").trim();
  }

  static class TestCase {
    final String name;
    final String query;
    final List<String> expectedQueryTexts;
    final String expectedQuerySummary;

    TestCase(
        String name, String query, List<String> expectedQueryTexts, String expectedQuerySummary) {
      this.name = name;
      this.query = query;
      this.expectedQueryTexts = expectedQueryTexts;
      this.expectedQuerySummary = expectedQuerySummary;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
