/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.Test;

class ElasticsearchDbAttributesGetterTest {

  private static final String SEARCH_BODY =
      "{\"query\":{\"match\":{\"title\":\"secret user data\"}}}";
  private static final String SANITIZED_BODY = "{\"query\":{\"match\":{\"title\":\"?\"}}}";

  @Test
  void sanitizesSearchQueryByDefault() {
    // the getter runs the captured body through the real masker and returns the sanitized result
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SANITIZED_BODY);
  }

  @Test
  void masksScalarValuesOfEveryType() {
    // strings, numbers, booleans and nulls are all masked to "?"
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);
    String body = "{\"from\":0,\"size\":10,\"track_total_hits\":true,\"after\":null}";

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(body, ContentType.APPLICATION_JSON))))
        .isEqualTo("{\"from\":\"?\",\"size\":\"?\",\"track_total_hits\":\"?\",\"after\":\"?\"}");
  }

  @Test
  void masksArrayElements() {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);
    String body = "{\"terms\":{\"tags\":[\"a\",\"b\",\"c\"]}}";

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(body, ContentType.APPLICATION_JSON))))
        .isEqualTo("{\"terms\":{\"tags\":[\"?\",\"?\",\"?\"]}}");
  }

  @Test
  void capturesRawBodyWhenSanitizationDisabled() {
    // sanitization explicitly disabled: capture the body verbatim
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, false);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SEARCH_BODY);
  }

  @Test
  void capturesNothingWhenCaptureDisabled() {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(false, true);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isNull();
  }

  @Test
  void capturesNothingForNonSearchEndpoint() {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "PUT",
            "/test-index/_doc/1",
            new ElasticsearchEndpointDefinition("PUT", new String[] {"/{index}/_doc/{id}"}, false),
            new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isNull();
  }

  @Test
  void doesNotReadNonRepeatableEntity() {
    // a non-repeatable entity must never be read, otherwise the request body would be consumed
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);
    HttpEntity entity =
        new InputStreamEntity(new ByteArrayInputStream(SEARCH_BODY.getBytes(UTF_8)));

    assertThat(getter.getDbQueryText(searchRequest(entity))).isNull();
  }

  @Test
  void dropsBodyWhenNotJson() {
    // a body that is not JSON must be dropped, never captured raw
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity("this is not json", ContentType.TEXT_PLAIN))))
        .isNull();
  }

  @Test
  void dropsBodyWhenMalformedJson() {
    // a truncated body must be dropped, never captured raw
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, true);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity("{\"query\":", ContentType.APPLICATION_JSON))))
        .isNull();
  }

  private static ElasticsearchRestRequest searchRequest(@Nullable HttpEntity httpEntity) {
    return ElasticsearchRestRequest.create(
        "POST",
        "/test-index/_search",
        new ElasticsearchEndpointDefinition("SEARCH", new String[] {"/{index}/_search"}, true),
        httpEntity);
  }
}
