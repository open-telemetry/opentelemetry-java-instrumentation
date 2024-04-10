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
  public String getSystem(ElasticTransportRequest s) {
    return DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String getUser(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String getName(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String getConnectionString(ElasticTransportRequest s) {
    return null;
  }

  @Override
  @Nullable
  public String getStatement(ElasticTransportRequest s) {
    return null;
  }

  @Override
  public String getOperation(ElasticTransportRequest action) {
    return action.getAction().getClass().getSimpleName();
  }
}
