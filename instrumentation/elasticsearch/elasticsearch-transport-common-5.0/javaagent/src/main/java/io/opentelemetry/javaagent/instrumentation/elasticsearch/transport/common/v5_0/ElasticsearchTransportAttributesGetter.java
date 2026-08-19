/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest, ActionResponse> {

  @Override
  public String getDbSystemName(ElasticTransportRequest request) {
    return DbSystemNameIncubatingValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(ElasticTransportRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(ElasticTransportRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(ElasticTransportRequest request) {
    return ElasticsearchTransportOperationNames.operationName(
        request.getAction().getClass().getSimpleName());
  }

  @Deprecated
  @Override
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(ElasticTransportRequest request) {
    // frozen: old semantic conventions keep reporting the action class name
    return request.getAction().getClass().getSimpleName();
  }
}
