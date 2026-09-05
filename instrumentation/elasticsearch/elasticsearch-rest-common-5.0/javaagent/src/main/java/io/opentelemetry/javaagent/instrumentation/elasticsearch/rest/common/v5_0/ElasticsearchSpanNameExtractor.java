/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class ElasticsearchSpanNameExtractor implements SpanNameExtractor<ElasticsearchRestRequest> {

  private final ElasticsearchDbAttributesGetter dbAttributesGetter;

  ElasticsearchSpanNameExtractor(ElasticsearchDbAttributesGetter dbAttributesGetter) {
    this.dbAttributesGetter = dbAttributesGetter;
  }

  @Override
  public String extract(ElasticsearchRestRequest elasticsearchRestRequest) {
    String name = dbAttributesGetter.getDbOperationName(elasticsearchRestRequest);
    return name != null ? name : elasticsearchRestRequest.getMethod();
  }
}
