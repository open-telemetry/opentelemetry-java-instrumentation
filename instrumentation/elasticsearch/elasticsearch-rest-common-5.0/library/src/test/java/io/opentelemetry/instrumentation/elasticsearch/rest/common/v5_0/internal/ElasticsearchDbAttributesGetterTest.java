/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ElasticsearchDbAttributesGetterTest {

  private static final String SEARCH_BODY =
      "{\"query\":{\"match\":{\"title\":\"secret user data\"}}}";
  private static final String SANITIZED_BODY = "{\"query\":{\"match\":{\"title\":\"?\"}}}";

  /** Records the bodies it is given and returns whatever it was configured to return. */
  private static class RecordingSanitizer implements UnaryOperator<String> {
    final List<String> sanitized = new ArrayList<>();
    final String result;

    RecordingSanitizer(String result) {
      this.result = result;
    }

    @Override
    public String apply(String body) {
      sanitized.add(body);
      return result;
    }
  }

  @Test
  void returnsTheSanitizedBody() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SANITIZED_BODY);
    assertThat(sanitizer.sanitized).containsExactly(SEARCH_BODY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/_search",
        "_search",
        "/test-index/_search?preference=local",
        "test-index/_search?preference=local",
        "/_msearch",
        "/test-index/_msearch",
        "/_search/template",
        "_search/template",
        "/test-index/_search/template",
        "/_msearch/template",
        "/test-index/_msearch/template",
        "/_async_search",
        "/test-index/_async_search",
        "/test-index/_doc/_search",
        "test-index/_doc/_search",
        "/_render/template",
        "/_render/template/private-template",
        "/test-index/_terms_enum",
        "/test-index/_eql/search",
        "test-index/_eql/search"
      })
  void recognizesSearchPathWithoutEndpointDefinition(String endpoint) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST", endpoint, null, new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isEqualTo(SANITIZED_BODY);
    assertThat(sanitizer.sanitized).containsExactly(SEARCH_BODY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/_search/scroll",
        "/test-index/_rollup_search",
        "/_application/search_application/private-app/_search"
      })
  void rejectsNonSearchPathWithoutEndpointDefinition(String endpoint) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST", endpoint, null, new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void dropsBodyWhenTheSanitizerRejectsIt() {
    // the sanitizer returns null when it cannot sanitize the body, which must never fall back to
    // capturing it raw
    RecordingSanitizer sanitizer = new RecordingSanitizer(null);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isNull();
  }

  @ParameterizedTest
  @MethodSource("multiSearchEndpoints")
  void joinsMultiSearchNdJsonLinesBeforeSanitizing(
      String endpointName, String requestPath, String endpointRoute) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    String body =
        "{\"index\":\"private-index\"}\n"
            + "{\"query\":{\"match\":{\"title\":\"secret\"}}}\n"
            + "{}\n"
            + "{\"id\":\"private-template\"}\n";
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST",
            requestPath,
            new ElasticsearchEndpointDefinition(endpointName, new String[] {endpointRoute}, true),
            new StringEntity(body, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isEqualTo(SANITIZED_BODY);
    // the line breaks are dropped while reading, so the sanitizer sees the values back to back
    assertThat(sanitizer.sanitized)
        .containsExactly(
            "{\"index\":\"private-index\"}"
                + "{\"query\":{\"match\":{\"title\":\"secret\"}}}"
                + "{}"
                + "{\"id\":\"private-template\"}");
  }

  @Test
  void capturesRawBodyWhenSanitizationDisabled() {
    // sanitization explicitly disabled: capture the body verbatim
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, null);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SEARCH_BODY);
  }

  @Test
  void capturesNothingWhenCaptureDisabled() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(false, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void capturesNothingForNonSearchEndpoint() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "PUT",
            "/test-index/_doc/1",
            new ElasticsearchEndpointDefinition("PUT", new String[] {"/{index}/_doc/{id}"}, false),
            new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void doesNotReadNonRepeatableEntity() {
    // a non-repeatable entity must never be read, otherwise the request body would be consumed
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    HttpEntity entity =
        new InputStreamEntity(new ByteArrayInputStream(SEARCH_BODY.getBytes(UTF_8)));

    assertThat(getter.getDbQueryText(searchRequest(entity))).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  private static Stream<Arguments> multiSearchEndpoints() {
    return Stream.of(
        argumentSet("_msearch", "msearch", "/test-index/_msearch", "/{index}/_msearch"),
        argumentSet(
            "_msearch/template",
            "msearch_template",
            "/test-index/_msearch/template",
            "/{index}/_msearch/template"));
  }

  private static ElasticsearchRestRequest searchRequest(HttpEntity httpEntity) {
    return ElasticsearchRestRequest.create(
        "POST",
        "/test-index/_search",
        new ElasticsearchEndpointDefinition("SEARCH", new String[] {"/{index}/_search"}, true),
        httpEntity);
  }
}
