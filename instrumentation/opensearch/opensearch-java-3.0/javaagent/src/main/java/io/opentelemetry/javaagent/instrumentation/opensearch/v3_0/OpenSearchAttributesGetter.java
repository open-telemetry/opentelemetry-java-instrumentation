/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

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
  public String getServerAddress(OpenSearchRequest request) {
    // old semantic conventions do not record the server at all
    return emitStableDatabaseSemconv() ? request.getServerAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(OpenSearchRequest request) {
    // a target that names several endpoints already carries the port of each of them
    return emitStableDatabaseSemconv() ? request.getServerPort() : null;
  }
}
