/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import org.opensearch.client.opensearch._types.OpenSearchException;

final class OpenSearchAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRequest, Void> {

  @Override
  public String getDbSystemName(OpenSearchRequest request) {
    return DbSystemNameIncubatingValues.OPENSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(OpenSearchRequest request) {
    if (request.getBody() == null) {
      // fall back to method and endpoint if capturing the query body is disabled or if the body is
      // not available
      return request.getMethod() + " " + request.getEndpoint();
    }
    return request.getBody();
  }

  @Override
  @Nullable
  public String getDbOperationName(OpenSearchRequest request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String getErrorType(
      OpenSearchRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (error instanceof CompletionException) {
      error = error.getCause();
    }
    if (error instanceof OpenSearchException) {
      int statusCode = ((OpenSearchException) error).status();
      if (statusCode >= 400 || statusCode < 100) {
        return Integer.toString(statusCode);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(OpenSearchRequest request) {
    return emitStableDatabaseSemconv() ? request.getServerAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(OpenSearchRequest request) {
    return emitStableDatabaseSemconv() ? request.getServerPort() : null;
  }
}
