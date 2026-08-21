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
    if (!(error instanceof ElasticsearchException)) {
      return null;
    }
    ElasticsearchException esError = (ElasticsearchException) error;
    if (esError instanceof TransportException) {
      // TransportException.status() derives its value from unwrapCause(); only use the status
      // when the unwrapped cause is itself a status-bearing ElasticsearchException.
      Throwable cause = esError.unwrapCause();
      if (cause == error || !(cause instanceof ElasticsearchException)) {
        return null;
      }
      esError = (ElasticsearchException) cause;
    }
    // Only use the status when this class explicitly declares status() rather than inheriting
    // the base class default, which always synthesizes INTERNAL_SERVER_ERROR.
    try {
      if (esError.getClass().getMethod("status").getDeclaringClass()
          == ElasticsearchException.class) {
        return null;
      }
    } catch (NoSuchMethodException ignored) {
      return null;
    }
    int statusCode = esError.status().getStatus();
    if (statusCode >= 400 || statusCode < 100) {
      return Integer.toString(statusCode);
    }
    return null;
  }
}
