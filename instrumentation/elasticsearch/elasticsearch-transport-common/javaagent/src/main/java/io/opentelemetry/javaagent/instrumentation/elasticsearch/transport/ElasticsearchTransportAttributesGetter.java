/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest> {

  @Override
  public String getSystem(ElasticTransportRequest request) {
    return DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(ElasticTransportRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getName(ElasticTransportRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getNamespace(ElasticTransportRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(ElasticTransportRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getStatement(ElasticTransportRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(ElasticTransportRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getOperation(ElasticTransportRequest request) {
    return request.getAction().getClass().getSimpleName();
  }

  @Override
  public String getDbOperationName(ElasticTransportRequest request) {
    return request.getAction().getClass().getSimpleName();
  }
}
