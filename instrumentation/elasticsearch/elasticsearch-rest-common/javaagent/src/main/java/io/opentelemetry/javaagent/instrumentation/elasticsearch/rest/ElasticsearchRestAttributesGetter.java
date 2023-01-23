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
  public String getSystem(ElasticsearchRestRequest request) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String getUser(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getName(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getConnectionString(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getStatement(ElasticsearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  @Nullable
  public String getOperation(ElasticsearchRestRequest request) {
    return request.getMethod();
  }
}
