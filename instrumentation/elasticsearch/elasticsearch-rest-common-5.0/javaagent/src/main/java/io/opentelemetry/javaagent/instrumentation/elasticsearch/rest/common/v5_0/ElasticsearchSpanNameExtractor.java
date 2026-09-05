/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class ElasticsearchSpanNameExtractor implements SpanNameExtractor<ElasticsearchRestRequest> {

  private final ElasticsearchDbAttributesGetter dbAttributesGetter;
  private final SpanNameExtractor<ElasticsearchRestRequest> stableDelegate;

  ElasticsearchSpanNameExtractor(ElasticsearchDbAttributesGetter dbAttributesGetter) {
    this.dbAttributesGetter = dbAttributesGetter;
    stableDelegate = DbClientSpanNameExtractor.create(dbAttributesGetter);
  }

  @Override
  public String extract(ElasticsearchRestRequest elasticsearchRestRequest) {
    if (emitStableDatabaseSemconv()) {
      return stableDelegate.extract(elasticsearchRestRequest);
    }
    String name = dbAttributesGetter.getDbOperationName(elasticsearchRestRequest);
    return name != null ? name : elasticsearchRestRequest.getMethod();
  }
}
