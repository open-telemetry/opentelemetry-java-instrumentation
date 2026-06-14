/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class ElasticsearchSpanNameExtractor implements SpanNameExtractor<ElasticsearchRestRequest> {

  private final ElasticsearchDbAttributesGetter dbAttributesGetter;

  ElasticsearchSpanNameExtractor(ElasticsearchDbAttributesGetter dbAttributesGetter) {
    this.dbAttributesGetter = dbAttributesGetter;
  }

  @SuppressWarnings("deprecation") // getDbOperation is used for old semconv span names
  @Override
  public String extract(ElasticsearchRestRequest elasticsearchRestRequest) {
    String name =
        emitStableDatabaseSemconv()
            ? dbAttributesGetter.getDbOperationName(elasticsearchRestRequest)
            : dbAttributesGetter.getDbOperation(elasticsearchRestRequest);
    return name != null ? name : elasticsearchRestRequest.getMethod();
  }
}
