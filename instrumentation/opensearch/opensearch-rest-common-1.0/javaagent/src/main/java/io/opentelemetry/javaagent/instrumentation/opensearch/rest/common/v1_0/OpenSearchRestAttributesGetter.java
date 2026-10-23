/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest, OpenSearchRestResponse> {

  private final OpenSearchRestInstrumenterFactory.ExceptionStatusCodeExtractor
      exceptionStatusCodeExtractor;

  OpenSearchRestAttributesGetter(
      OpenSearchRestInstrumenterFactory.ExceptionStatusCodeExtractor exceptionStatusCodeExtractor) {
    this.exceptionStatusCodeExtractor = exceptionStatusCodeExtractor;
  }

  @Override
  public String getDbSystemName(OpenSearchRestRequest request) {
    return DbSystemNameIncubatingValues.OPENSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(OpenSearchRestRequest request) {
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getErrorType(
      OpenSearchRestRequest request,
      @Nullable OpenSearchRestResponse response,
      @Nullable Throwable error) {

    if (response != null) {
      int statusCode = response.getStatusCode();
      if (statusCode >= 400 || statusCode < 100) {
        return Integer.toString(statusCode);
      }
    }
    return DbClientAttributesGetter.super.getErrorType(request, response, error);
  }

  @Override
  @Nullable
  public String getDbResponseStatusCode(
      OpenSearchRestRequest request,
      @Nullable OpenSearchRestResponse response,
      @Nullable Throwable error) {
    Integer statusCode = null;
    if (response != null) {
      statusCode = response.getStatusCode();
    } else if (error != null) {
      statusCode = exceptionStatusCodeExtractor.getStatusCode(error);
    }
    return statusCode == null ? null : Integer.toString(statusCode);
  }

  @Override
  @Nullable
  public String getServerAddress(OpenSearchRestRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target != null ? target.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(OpenSearchRestRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target != null ? target.getPort() : null;
  }
}
