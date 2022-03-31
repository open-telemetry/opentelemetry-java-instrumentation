/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class ElasticsearchRestAttributesGetter
    implements DbClientAttributesGetter<ElasticsearchRestRequest> {

  @Override
  public String system(ElasticsearchRestRequest request) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String user(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String name(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String statement(ElasticsearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  @Nullable
  public String operation(ElasticsearchRestRequest request) {
    return request.getMethod();
  }
}
