/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.internal;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ElasticsearchSpanNameExtractor implements SpanNameExtractor<ElasticsearchRestRequest> {

  private final ElasticsearchDbAttributeGetter dbAttributeGetter;

  public ElasticsearchSpanNameExtractor(ElasticsearchDbAttributeGetter dbAttributeGetter) {
    this.dbAttributeGetter = dbAttributeGetter;
  }

  @Override
  public String extract(ElasticsearchRestRequest elasticsearchRestRequest) {
    String name = dbAttributeGetter.getOperation(elasticsearchRestRequest);
    return name != null ? name : elasticsearchRestRequest.getMethod();
  }
}
