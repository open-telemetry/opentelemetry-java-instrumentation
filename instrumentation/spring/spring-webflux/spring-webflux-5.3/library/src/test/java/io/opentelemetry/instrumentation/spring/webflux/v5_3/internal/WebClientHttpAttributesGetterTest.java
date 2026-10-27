/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebClientHttpAttributesGetterTest {

  @Test
  void extractsPathFromRelativeAndAbsoluteTemplates() {
    assertThat(WebClientHttpAttributesGetter.extractPathTemplate("/accounts/{id}"))
        .isEqualTo("/accounts/{id}");
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate("https://example.com/accounts/{id}"))
        .isEqualTo("/accounts/{id}");
  }

  @Test
  void excludesAuthorityWithTemplatedScheme() {
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "{scheme}://user:secret@example.com/accounts/{id}"))
        .isEqualTo("/accounts/{id}");
  }

  @Test
  void doesNotCaptureOpaqueTemplateWithSchemeDelimiterInVariable() {
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "{scheme}user:secret@example.com/accounts/{id}"))
        .isNull();
  }

  @Test
  void doesNotCaptureEmptyOrInvalidTemplate() {
    assertThat(WebClientHttpAttributesGetter.extractPathTemplate("")).isNull();
    assertThat(WebClientHttpAttributesGetter.extractPathTemplate("https://")).isNull();
  }

  @Test
  void stripsQueryAndFragmentFromRelativeAndAbsoluteTemplates() {
    assertThat(WebClientHttpAttributesGetter.extractPathTemplate("/accounts/{id}?sig=secret"))
        .isEqualTo("/accounts/{id}");
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "/accounts/{id}#https://redirect.example/{id}"))
        .isEqualTo("/accounts/{id}");
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "https://example.com/accounts/{id}?next=https://redirect.example/{id}#fragment/path"))
        .isEqualTo("/accounts/{id}");
  }

  @Test
  void doesNotDeriveAbsolutePathFromQueryOrFragment() {
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "https://example.com?next=https://redirect.example/{id}"))
        .isNull();
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "https://example.com#https://redirect.example/{id}"))
        .isNull();
    assertThat(
            WebClientHttpAttributesGetter.extractPathTemplate(
                "https://example.com?next=https://redirect.example/{id}#fragment/path"))
        .isNull();
  }
}
