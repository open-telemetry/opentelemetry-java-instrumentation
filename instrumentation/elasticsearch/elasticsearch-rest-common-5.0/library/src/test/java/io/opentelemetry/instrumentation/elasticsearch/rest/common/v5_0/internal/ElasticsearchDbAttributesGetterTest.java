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
}
