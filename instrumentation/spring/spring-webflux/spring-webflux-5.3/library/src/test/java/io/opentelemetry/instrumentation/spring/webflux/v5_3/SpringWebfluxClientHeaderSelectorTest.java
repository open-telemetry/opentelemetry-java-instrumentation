/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SpringWebfluxClientHeaderSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesHeadersMatchingSelectorPatterns() {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build();

    sendRequest(telemetry);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .containsExactly("request-value");
    assertThat(attributes.get(stringArrayKey("http.request.header.x-secret-token"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .containsExactly("response-value");
    assertThat(attributes.get(stringArrayKey("http.response.header.x-secret-token"))).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void capturesHeadersConfiguredByName() {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(asList("X-Test-Request", "Authorization"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build();

    sendRequest(telemetry);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .containsExactly("request-value");
    // capturing Authorization here is what makes the assertion that it is absent in
    // deprecatedSettersMatchHeaderNamesLiterally meaningful
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization")))
        .containsExactly("secret-value");
    assertThat(attributes.get(stringArrayKey("http.request.header.x-secret-token"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .containsExactly("response-value");
    assertThat(attributes.get(stringArrayKey("http.response.header.x-secret-token"))).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void deprecatedSettersMatchHeaderNamesLiterally() {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build();

    sendRequest(telemetry);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    // "*" is dropped while the selector is built, so it captures nothing; Authorization is in the
    // request so that treating "*" as a glob would capture it
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(attributes.asMap().keySet())
        .noneMatch(key -> key.getKey().startsWith("http.request.header."))
        .noneMatch(key -> key.getKey().startsWith("http.response.header."));
  }

  private static void sendRequest(SpringWebfluxClientTelemetry telemetry) {
    WebClient client =
        WebClient.builder()
            .filters(telemetry::addFilter)
            .exchangeFunction(
                request ->
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header("X-Test-Response", "response-value")
                            .header("X-Secret-Token", "secret-value")
                            .build()))
            .build();

    client
        .get()
        .uri("http://localhost:8080/test")
        .header("X-Test-Request", "request-value")
        .header("X-Secret-Token", "secret-value")
        .header("Authorization", "secret-value")
        .retrieve()
        .toBodilessEntity()
        .block();
  }
}
