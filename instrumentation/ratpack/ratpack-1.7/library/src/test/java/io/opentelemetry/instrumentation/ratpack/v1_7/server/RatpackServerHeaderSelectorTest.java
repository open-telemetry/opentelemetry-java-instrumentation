/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;

class RatpackServerHeaderSelectorTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void capturesEveryHeaderNotExcluded() throws Exception {
    RatpackServerTelemetry telemetry =
        RatpackServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setExcluded(singletonList("host")).build())
            .setResponseHeaders(
                IncludeExclude.builder().setExcluded(singletonList("content-*")).build())
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.response.header.content-type"))).isNull();
  }

  @Test
  void capturesHeadersMatchingWildcardPattern() throws Exception {
    RatpackServerTelemetry telemetry =
        RatpackServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(
                IncludeExclude.builder().setIncluded(singletonList("x-test-*")).build())
            .setResponseHeaders(
                IncludeExclude.builder()
                    .setIncluded(asList("x-test-*", "content-*"))
                    .setExcluded(singletonList("content-type"))
                    .build())
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.response.header.content-type"))).isNull();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void capturesHeadersConfiguredByName() throws Exception {
    RatpackServerTelemetry telemetry =
        RatpackServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    RatpackServerTelemetry telemetry =
        RatpackServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build();

    Attributes attributes = handleRequest(telemetry);

    // implementing header name enumeration must not turn the deprecated exact-name setters into
    // wildcard matching, since "*" is a legal header name character and capturing every header
    // would expose credentials
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response"))).isNull();
  }

  private static Attributes handleRequest(RatpackServerTelemetry telemetry) throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(Registry.of(telemetry::configureRegistry));
              spec.handlers(
                  chain ->
                      chain.get(
                          "test",
                          ctx -> {
                            ctx.getResponse().getHeaders().set("X-Test-Response", "test");
                            ctx.render("hi");
                          }));
            });
    cleanup.deferCleanup(app);

    assertThat(
            app.getHttpClient()
                .requestSpec(
                    spec ->
                        spec.getHeaders()
                            .set("X-Test-Request", "test")
                            .set("Authorization", "Bearer secret"))
                .get("test")
                .getBody()
                .getText())
        .isEqualTo("hi");

    return testing.waitForTraces(1).get(0).get(0).getAttributes();
  }
}
