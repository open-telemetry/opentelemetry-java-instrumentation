/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ElasticsearchDbAttributesGetterTest {

  private final ElasticsearchDbAttributesGetter underTest =
      new ElasticsearchDbAttributesGetter(false);

  @Test
  void shouldInferLowCardinalityOperationNameFromApiPath() {
    assertThat(
            underTest.getDbOperationName(ElasticsearchRestRequest.create("GET", "_cluster/health")))
        .isEqualTo("cluster.health");
    assertThat(
            underTest.getDbOperationName(ElasticsearchRestRequest.create("GET", "/_cat/indices?v")))
        .isEqualTo("cat.indices");
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("POST", "test-index/_search")))
        .isEqualTo("search");
  }

  @Test
  void shouldInferDocumentOperationNameFromMethodAndApiPath() {
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("GET", "test-index/_doc/document-id")))
        .isEqualTo("get");
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("PUT", "test-index/_doc/document-id")))
        .isEqualTo("index");
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("DELETE", "test-index/_doc/document-id")))
        .isEqualTo("delete");
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("POST", "test-index/_create/document-id")))
        .isEqualTo("create");
    assertThat(
            underTest.getDbOperationName(
                ElasticsearchRestRequest.create("POST", "test-index/_update/document-id")))
        .isEqualTo("update");
  }

  @SuppressWarnings("deprecation") // getDbOperation is used for old semconv
  @Test
  void shouldKeepOldLowLevelOperationUnavailable() {
    assertThat(underTest.getDbOperation(ElasticsearchRestRequest.create("GET", "_cluster/health")))
        .isNull();
  }

  @Test
  void shouldStripLeadingSlashAndQueryString() {
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "/_cat/indices"))
        .isEqualTo("cat.indices");
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "_search?size=10"))
        .isEqualTo("search");
    assertThat(
            ElasticsearchDbAttributesGetter.inferOperationName(
                "GET", "/test-index/_search?from=0&size=10"))
        .isEqualTo("search");
  }

  @Test
  void shouldInferGroupedApiNameWithAndWithoutSubcommand() {
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "_nodes/stats"))
        .isEqualTo("nodes.stats");
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "_nodes"))
        .isEqualTo("nodes");
    // a following underscore segment is not treated as a subcommand
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("POST", "_tasks/_cancel"))
        .isEqualTo("tasks");
    // a non-grouped api keeps just the api segment
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("POST", "_search/scroll"))
        .isEqualTo("search");
    // a trailing slash does not produce a trailing dot
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "_nodes/"))
        .isEqualTo("nodes");
  }

  @Test
  void shouldReturnNullWhenNoApiSegmentPresent() {
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "")).isNull();
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "/")).isNull();
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "test-index")).isNull();
    assertThat(ElasticsearchDbAttributesGetter.inferOperationName("GET", "test-index/doc-id"))
        .isNull();
  }
}
