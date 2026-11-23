/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WebClientUriTemplateParserTest {

  @ParameterizedTest
  @MethodSource("acceptedTemplates")
  void extractsPathTemplate(String template, String expected) {
    assertThat(WebClientUriTemplateParser.extractPathTemplate(template)).isEqualTo(expected);
  }

  private static Stream<Arguments> acceptedTemplates() {
    return Stream.of(
        argumentSet(
            "path with query and fragment",
            "/accounts/{id}?sig={sig}#details",
            "/accounts/{id}?sig={sig}#details"),
        argumentSet("path with empty query and fragment", "/accounts/{id}?#", "/accounts/{id}?#"),
        argumentSet(
            "path preserves slashes and encoded query",
            "/accounts//{id}?x=one&x=%2F#section/{part}",
            "/accounts//{id}?x=one&x=%2F#section/{part}"),
        argumentSet(
            "IPv6 authority",
            "https://[2001:db8::1]:8443/accounts/{id}?x={value}&x=%2F#fragment/path",
            "/accounts/{id}?x={value}&x=%2F#fragment/path"),
        argumentSet(
            "userinfo in authority",
            "//user:secret@[2001:db8::1]:8443/accounts/{id}?x=1",
            "/accounts/{id}?x=1"),
        argumentSet(
            "nested expressions",
            "{scheme}://{host:[a-z/?#]+}/accounts/{id:[0-9]{1,3}}?next={value:/a?b#c}",
            "/accounts/{id:[0-9]{1,3}}?next={value:/a?b#c}"),
        argumentSet(
            "valid templated scheme",
            "a1+.-{scheme}://example.com/accounts/{id}",
            "/accounts/{id}"),
        argumentSet("path variable", "/accounts/{id}", "/accounts/{id}"),
        argumentSet(
            "query variable", "/accounts/123?filter={filter}", "/accounts/123?filter={filter}"),
        argumentSet(
            "scheme variable outside path", "{scheme}://example.com/accounts/123", "/accounts/123"),
        argumentSet(
            "authority variable outside path", "https://{host}/accounts/123", "/accounts/123"));
  }

  @ParameterizedTest
  @MethodSource("rejectedTemplates")
  void doesNotExtractPathTemplate(String template) {
    assertThat(WebClientUriTemplateParser.extractPathTemplate(template)).isNull();
  }

  private static Stream<Arguments> rejectedTemplates() {
    return Stream.of(
        argumentSet("scheme begins with digit", "1https://example.com/{id}"),
        argumentSet("empty scheme", "://example.com/{id}"),
        argumentSet("scheme contains underscore", "ht_tps://example.com/{id}"),
        argumentSet("scheme contains extra colon", "https:other://example.com/{id}"),
        argumentSet("host variable without path", "https://{host}"),
        argumentSet("scheme-relative host variable without path", "//{host}"),
        argumentSet("host variable with query but no path", "https://{host}?next=/path"),
        argumentSet("host variable with fragment but no path", "https://{host}#fragment/path"),
        argumentSet("host variable with double-slash path", "https://{host}//path"),
        argumentSet("scheme-relative host variable with double-slash path", "//{host}//path"),
        argumentSet("empty path before variable", "https:///{id}"),
        argumentSet("scheme-relative empty path before variable", "///{id}"),
        argumentSet("literal path without variable", "/health"),
        argumentSet("literal path and query without variable", "/accounts/123?sig=secret"),
        argumentSet("absolute URI without variable", "https://example.com/accounts/123?sig=secret"),
        argumentSet(
            "scheme-relative URI without variable", "//example.com/accounts/123?sig=secret"),
        argumentSet("encoded braces are not a variable", "/accounts/%7Bid%7D"),
        argumentSet("empty template", ""),
        argumentSet("single URL variable without path", "{url}"),
        argumentSet("relative path", "accounts/{id}"),
        argumentSet("opaque scheme", "mailto:user@example.com/path"),
        argumentSet(
            "ambiguous userinfo before authority", "{scheme}user:secret@example.com/accounts/{id}"),
        argumentSet("authority without path", "https://example.com"),
        argumentSet("scheme-relative authority without path", "//example.com"),
        argumentSet("query without path", "https://example.com?next=/path"),
        argumentSet("fragment without path", "https://example.com#fragment/path"),
        argumentSet("absolute URI with double-slash path", "https://example.com//path"),
        argumentSet("scheme-relative URI with double-slash path", "//example.com//path"),
        argumentSet("unclosed expression", "/accounts/{id"),
        argumentSet("empty expression", "/accounts/{}"),
        argumentSet("empty variable pattern", "/accounts/{id:}"),
        argumentSet("invalid variable name", "/accounts/{id name}"),
        argumentSet("extra closing brace", "https://example.com/{id}}"),
        argumentSet(
            "opaque templated scheme", "{scheme:[a-z://]+}user:secret@example.com/accounts/{id}"));
  }
}
