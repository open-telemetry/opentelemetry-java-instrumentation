/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlQuerySanitizerTest {

  private static final Set<String> TEST_SENSITIVE_PARAMS =
      new HashSet<>(asList("secret", "apiKey", "token"));

  @Test
  void redactQueryString_shouldReturnNullForNullInput() {
    assertThat(UrlQuerySanitizer.redactQueryString(null, TEST_SENSITIVE_PARAMS)).isNull();
  }

  @Test
  void redactQueryString_shouldReturnEmptyForEmptyInput() {
    assertThat(UrlQuerySanitizer.redactQueryString("", TEST_SENSITIVE_PARAMS)).isEmpty();
  }

  @Test
  void redactQueryString_shouldReturnOriginalWhenNoSensitiveParams() {
    assertThat(UrlQuerySanitizer.redactQueryString("secret=value&apiKey=key", emptySet()))
        .isEqualTo("secret=value&apiKey=key");
  }

  @ParameterizedTest
  @CsvSource({
    // No sensitive params - optimization path
    "paramA=valA&paramB=valB, paramA=valA&paramB=valB",
    // Single sensitive param - basic redaction
    "secret=mySecret123, secret=REDACTED",
    // Sensitive with non-sensitive params
    "other=val&secret=val2, other=val&secret=REDACTED",
    // Multiple same sensitive param - reset logic
    "secret=val1&secret=val2, secret=REDACTED&secret=REDACTED",
    // Multiple different sensitive params - set lookup
    "secret=s1&apiKey=k1&token=t1, secret=REDACTED&apiKey=REDACTED&token=REDACTED",
    // Fragment handling - '#' branch
    "param=val&token=t#ref, param=val&token=REDACTED#ref",
    // Empty value with next param - value skipping with immediate delimiter
    "secret=&param=val, secret=REDACTED&param=val",
    // Params without values (no '=')
    "flag&secret=val&other, flag&secret=REDACTED&other",
    // Empty delimiters - consecutive '&'
    "&&secret=val, &&secret=REDACTED",
    // Partial match - exact name matching
    "mySecret=value, mySecret=value"
  })
  void redactQueryString_shouldRedactSensitiveParams(String input, String expected) {
    assertThat(UrlQuerySanitizer.redactQueryString(input, TEST_SENSITIVE_PARAMS))
        .isEqualTo(expected);
  }

  @Test
  void redactUrl_shouldReturnOriginalWhenNoSensitiveParams() {
    String url = "https://example.com?secret=value";
    assertThat(UrlQuerySanitizer.redactUrl(url, emptySet())).isEqualTo(url);
  }

  @Test
  void redactUrl_shouldReturnOriginalWhenNoQueryString() {
    String url = "https://example.com";
    assertThat(UrlQuerySanitizer.redactUrl(url, TEST_SENSITIVE_PARAMS)).isEqualTo(url);
  }

  @Test
  void redactUrl_shouldReturnOriginalWhenEmptyQueryString() {
    String url = "https://example.com?";
    assertThat(UrlQuerySanitizer.redactUrl(url, TEST_SENSITIVE_PARAMS)).isEqualTo(url);
  }

  @Test
  void redactUrl_shouldReturnOriginalWhenNoSensitiveParamsPresent() {
    String url = "https://example.com?foo=bar";
    assertThat(UrlQuerySanitizer.redactUrl(url, TEST_SENSITIVE_PARAMS)).isEqualTo(url);
  }

  @ParameterizedTest
  @CsvSource({
    // Basic URL structure - scheme and host preservation
    "https://example.com?secret=val, https://example.com?secret=REDACTED",
    // With path - path preservation
    "https://example.com/path/to/resource?secret=val, https://example.com/path/to/resource?secret=REDACTED",
    // With port - port preservation
    "https://example.com:8080?apiKey=val, https://example.com:8080?apiKey=REDACTED",
    // Integration - verify redactInternal delegation works (fragment, multiple params)
    "https://example.com?a=1&token=t#ref, https://example.com?a=1&token=REDACTED#ref"
  })
  void redactUrl_shouldRedactSensitiveParams(String input, String expected) {
    assertThat(UrlQuerySanitizer.redactUrl(input, TEST_SENSITIVE_PARAMS)).isEqualTo(expected);
  }
}
