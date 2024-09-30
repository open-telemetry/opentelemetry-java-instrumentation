/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest> {

  @Override
  public String getDbSystem(ElasticTransportRequest elasticTransportRequest) {
    return ELASTICSEARCH;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(ElasticTransportRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(ElasticTransportRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(ElasticTransportRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(ElasticTransportRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(ElasticTransportRequest request) {
    return request.getAction().getClass().getSimpleName();
  }
}
