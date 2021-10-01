/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;

final class ElasticsearchTransportAttributesExtractor
    extends DbAttributesExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  protected String system(ElasticTransportRequest s) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  protected @Nullable String user(ElasticTransportRequest s) {
    return null;
  }

  @Override
  protected @Nullable String name(ElasticTransportRequest s) {
    return null;
  }

  @Override
  protected @Nullable String connectionString(ElasticTransportRequest s) {
    return null;
  }

  @Override
  protected @Nullable String statement(ElasticTransportRequest s) {
    return null;
  }

  @Override
  protected @Nullable String operation(ElasticTransportRequest action) {
    return action.getAction().getClass().getSimpleName();
  }
}
