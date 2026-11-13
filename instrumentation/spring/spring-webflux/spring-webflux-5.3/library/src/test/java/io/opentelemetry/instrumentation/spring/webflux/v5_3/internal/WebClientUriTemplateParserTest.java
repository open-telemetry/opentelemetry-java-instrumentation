/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebClientUriTemplateParserTest {

  @Test
  void preservesAbsolutePathReferences() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id}?sig={sig}#details"))
        .isEqualTo("/accounts/{id}?sig={sig}#details");
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id}?#"))
        .isEqualTo("/accounts/{id}?#");
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "/accounts//{id}?x=one&x=%2F#section/{part}"))
        .isEqualTo("/accounts//{id}?x=one&x=%2F#section/{part}");
  }

  @Test
  void excludesHierarchicalAuthorityAndPreservesSuffix() {
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "https://[2001:db8::1]:8443/accounts/{id}?x={value}&x=%2F#fragment/path"))
        .isEqualTo("/accounts/{id}?x={value}&x=%2F#fragment/path");
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "//user:secret@[2001:db8::1]:8443/accounts/{id}?x=1"))
        .isEqualTo("/accounts/{id}?x=1");
  }

  @Test
  void supportsTemplateBoundariesAndNestedExpressions() {
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "{scheme}://{host:[a-z/?#]+}/accounts/{id:[0-9]{1,3}}?next={value:/a?b#c}"))
        .isEqualTo("/accounts/{id:[0-9]{1,3}}?next={value:/a?b#c}");
  }

  @Test
  void validatesSchemeWhileFindingAuthority() {
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "a1+.-{scheme}://example.com/accounts/{id}"))
        .isEqualTo("/accounts/{id}");
    assertThat(WebClientUriTemplateParser.extractPathTemplate("1https://example.com/{id}"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("://example.com/{id}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("ht_tps://example.com/{id}"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https:other://example.com/{id}"))
        .isNull();
  }

  @Test
  void rejectsParameterizedTemplatesWithoutAnAbsolutePathReference() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://{host}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("//{host}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://{host}?next=/path"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://{host}#fragment/path"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://{host}//path")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("//{host}//path")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https:///{id}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("///{id}")).isNull();
  }

  @Test
  void requiresAtLeastOneUriVariable() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/health")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/123?sig=secret")).isNull();
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "https://example.com/accounts/123?sig=secret"))
        .isNull();
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate("//example.com/accounts/123?sig=secret"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/%7Bid%7D")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id}"))
        .isEqualTo("/accounts/{id}");
  }

  @Test
  void recognizesVariablesOutsideThePath() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/123?filter={filter}"))
        .isEqualTo("/accounts/123?filter={filter}");
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate("{scheme}://example.com/accounts/123"))
        .isEqualTo("/accounts/123");
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://{host}/accounts/123"))
        .isEqualTo("/accounts/123");
  }

  @Test
  void doesNotCaptureAmbiguousOrPathlessTemplates() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("{url}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("accounts/{id}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("mailto:user@example.com/path"))
        .isNull();
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "{scheme}user:secret@example.com/accounts/{id}"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://example.com")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("//example.com")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://example.com?next=/path"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://example.com#fragment/path"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://example.com//path"))
        .isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("//example.com//path")).isNull();
  }

  @Test
  void rejectsMalformedExpressionsAndOpaqueTemplatedSchemes() {
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id:}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("/accounts/{id name}")).isNull();
    assertThat(WebClientUriTemplateParser.extractPathTemplate("https://example.com/{id}}"))
        .isNull();
    assertThat(
            WebClientUriTemplateParser.extractPathTemplate(
                "{scheme:[a-z://]+}user:secret@example.com/accounts/{id}"))
        .isNull();
  }
}
