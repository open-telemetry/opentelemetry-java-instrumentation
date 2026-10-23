/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;

class WebClientHttpAttributesGetterTest {

  private static final String URI_TEMPLATE_ATTRIBUTE =
      "org.springframework.web.reactive.function.client.WebClient.uriTemplate";

  private final WebClientHttpAttributesGetter getter = new WebClientHttpAttributesGetter();

  @Test
  void stripsQueryAndFragmentFromRelativeAndAbsoluteTemplates() {
    assertThat(getUrlTemplate("/accounts/{id}?sig=secret")).isEqualTo("/accounts/{id}");
    assertThat(getUrlTemplate("/accounts/{id}#https://redirect.example/{id}"))
        .isEqualTo("/accounts/{id}");
    assertThat(
            getUrlTemplate(
                "https://example.com/accounts/{id}?next=https://redirect.example/{id}#fragment/path"))
        .isEqualTo("/accounts/{id}");
  }

  @Test
  void doesNotDeriveAbsolutePathFromQueryOrFragment() {
    assertThat(getUrlTemplate("https://example.com?next=https://redirect.example/{id}")).isNull();
    assertThat(getUrlTemplate("https://example.com#https://redirect.example/{id}")).isNull();
    assertThat(
            getUrlTemplate("https://example.com?next=https://redirect.example/{id}#fragment/path"))
        .isNull();
  }

  private String getUrlTemplate(String template) {
    ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("https://example.com"))
            .attribute(URI_TEMPLATE_ATTRIBUTE, template)
            .build();
    return getter.getUrlTemplate(request);
  }
}
