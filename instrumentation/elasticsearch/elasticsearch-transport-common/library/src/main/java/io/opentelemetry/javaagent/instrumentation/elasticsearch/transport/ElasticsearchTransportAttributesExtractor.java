/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

final class ElasticsearchTransportAttributesExtractor
    extends DbAttributesExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  protected String system(ElasticTransportRequest s) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  protected String user(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  protected String name(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  protected String connectionString(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  protected String operation(ElasticTransportRequest action) {
    return action.getAction().getClass().getSimpleName();
  }
}
