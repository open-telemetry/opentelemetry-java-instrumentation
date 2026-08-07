/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlSanitizerTest {

  private static final Set<String> TEST_SENSITIVE_PARAMS =
      new HashSet<>(asList("secret", "apiKey", "token"));

  private static final String URL_WITH_USER_INFO = "https://user:pass@example.com?secret=val";

  @ParameterizedTest
  @CsvSource({
    "https://user1:secret@github.com, https://REDACTED:REDACTED@github.com",
    "https://user1:secret@github.com/path/, https://REDACTED:REDACTED@github.com/path/",
    "https://user1:secret@github.com#test.html, https://REDACTED:REDACTED@github.com#test.html",
    "https://user1:secret@github.com?foo=b@r, https://REDACTED:REDACTED@github.com?foo=b@r",
    "https://user1:secret@github.com/p@th?foo=b@r, https://REDACTED:REDACTED@github.com/p@th?foo=b@r",
    "https://github.com/p@th?foo=b@r, https://github.com/p@th?foo=b@r",
    "https://github.com#t@st.html, https://github.com#t@st.html",
    "user1:secret@github.com, user1:secret@github.com",
    "https://github.com@, https://github.com@",
    "https://service.com?paramA=valA&paramB=valB, https://service.com?paramA=valA&paramB=valB",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7, https://service.com?AWSAccessKeyId=REDACTED",
    "https://service.com?Signature=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0%3A377, https://service.com?Signature=REDACTED",
    "https://service.com?X-Amz-Signature=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0, https://service.com?X-Amz-Signature=REDACTED",
    "https://service.com?X-Amz-Credential=AKIAIOSFODNN7%2F20260101%2Fus-east-1%2Fs3%2Faws4_request, https://service.com?X-Amz-Credential=REDACTED",
    "https://service.com?X-Amz-Security-Token=FwoGZXIvYXdzEBYaDG, https://service.com?X-Amz-Security-Token=REDACTED",
    "https://service.com?sig=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0, https://service.com?sig=REDACTED",
    "https://service.com?X-Goog-Signature=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0, https://service.com?X-Goog-Signature=REDACTED",
    "https://service.com?paramA=valA&AWSAccessKeyId=AKIAIOSFODNN7&paramB=valB, https://service.com?paramA=valA&AWSAccessKeyId=REDACTED&paramB=valB",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&paramA=valA, https://service.com?AWSAccessKeyId=REDACTED&paramA=valA",
    "https://service.com?paramA=valA&AWSAccessKeyId=AKIAIOSFODNN7, https://service.com?paramA=valA&AWSAccessKeyId=REDACTED",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&AWSAccessKeyId=ZGIAIOSFODNN7, https://service.com?AWSAccessKeyId=REDACTED&AWSAccessKeyId=REDACTED",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7#ref, https://service.com?AWSAccessKeyId=REDACTED#ref",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&aa&bb, https://service.com?AWSAccessKeyId=REDACTED&aa&bb",
    "https://service.com?aa&bb&AWSAccessKeyId=AKIAIOSFODNN7, https://service.com?aa&bb&AWSAccessKeyId=REDACTED",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&&, https://service.com?AWSAccessKeyId=REDACTED&&",
    "https://service.com?&&AWSAccessKeyId=AKIAIOSFODNN7, https://service.com?&&AWSAccessKeyId=REDACTED",
    "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&a&b#fragment, https://service.com?AWSAccessKeyId=REDACTED&a&b#fragment"
  })
  void shouldRedactUserInfoAndQueryParameters(String url, String expectedResult) {
    assertThat(UrlSanitizer.sanitizeUrl(url, HttpConstants.SENSITIVE_QUERY_PARAMETERS))
        .isEqualTo(expectedResult);
  }

  @Test
  void shouldReturnNullForNullInput() {
    assertThat(UrlSanitizer.sanitizeUrl(null, TEST_SENSITIVE_PARAMS)).isNull();
  }

  @Test
  void shouldReturnEmptyForEmptyInput() {
    assertThat(UrlSanitizer.sanitizeUrl("", TEST_SENSITIVE_PARAMS)).isEmpty();
  }

  @Test
  void shouldRedactUserInfoWhenNoSensitiveQueryParametersConfigured() {
    // query redaction early-returns on an empty parameter set, but the semantic conventions
    // require url.full to never carry credentials, so userinfo redaction is unconditional
    assertThat(UrlSanitizer.sanitizeUrl(URL_WITH_USER_INFO, emptySet()))
        .isEqualTo("https://REDACTED:REDACTED@example.com?secret=val");
  }

  @Test
  void shouldRedactBothUserInfoAndQueryParameters() {
    assertThat(UrlSanitizer.sanitizeUrl(URL_WITH_USER_INFO, TEST_SENSITIVE_PARAMS))
        .isEqualTo("https://REDACTED:REDACTED@example.com?secret=REDACTED");
  }

  @Test
  void shouldReturnOriginalWhenNoScheme() {
    assertThat(UrlSanitizer.sanitizeUrl("example.com/path", TEST_SENSITIVE_PARAMS))
        .isEqualTo("example.com/path");
  }

  @Test
  void shouldReturnOriginalWhenNoAuthority() {
    assertThat(UrlSanitizer.sanitizeUrl("mailto:someone@example.com", TEST_SENSITIVE_PARAMS))
        .isEqualTo("mailto:someone@example.com");
  }
}
