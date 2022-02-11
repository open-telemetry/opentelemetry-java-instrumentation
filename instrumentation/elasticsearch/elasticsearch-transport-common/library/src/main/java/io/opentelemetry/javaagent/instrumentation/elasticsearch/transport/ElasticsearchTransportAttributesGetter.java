/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest> {

  @Override
  public String system(ElasticTransportRequest s) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String user(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String name(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String statement(ElasticTransportRequest s) {
    return null;
  }

  @Override
  public String operation(ElasticTransportRequest action) {
    return action.getAction().getClass().getSimpleName();
  }
}
