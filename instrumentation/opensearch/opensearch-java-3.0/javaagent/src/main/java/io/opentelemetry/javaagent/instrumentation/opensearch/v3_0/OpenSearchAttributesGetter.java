/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
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
    Integer statusCode = getErrorCode(error);
    return statusCode != null && (statusCode >= 400 || statusCode < 100)
        ? statusCode.toString()
        : DbClientAttributesGetter.super.getErrorType(request, response, error);
  }

  @Override
  @Nullable
  public String getDbResponseStatusCode(
      OpenSearchRequest request, @Nullable Void response, @Nullable Throwable error) {
    Integer statusCode = getErrorCode(error);
    return statusCode != null ? statusCode.toString() : null;
  }

  @Override
  @Nullable
  public String getServerAddress(OpenSearchRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target != null ? target.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(OpenSearchRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target != null ? target.getPort() : null;
  }

  @Nullable
  private static Integer getErrorCode(@Nullable Throwable error) {
    if (error instanceof CompletionException) {
      error = error.getCause();
    }
    return error instanceof OpenSearchException ? ((OpenSearchException) error).status() : null;
  }
}
