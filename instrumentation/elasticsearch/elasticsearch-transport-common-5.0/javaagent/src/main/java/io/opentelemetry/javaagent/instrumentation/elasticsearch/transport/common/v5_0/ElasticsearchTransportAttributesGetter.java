/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.transport.TransportException;

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
    return request.getAction().getClass().getSimpleName();
  }

  @Override
  @Nullable
  public String getErrorType(
      ElasticTransportRequest request,
      @Nullable ActionResponse response,
      @Nullable Throwable error) {
    // TransportException.status() synthesizes 500 when there is no remote cause to unwrap.
    if (error instanceof TransportException
        && ((TransportException) error).unwrapCause() == error) {
      return null;
    }
    if (error instanceof ElasticsearchException) {
      int statusCode = ((ElasticsearchException) error).status().getStatus();
      if (statusCode >= 400 || statusCode < 100) {
        return Integer.toString(statusCode);
      }
    }
    return null;
  }
}
