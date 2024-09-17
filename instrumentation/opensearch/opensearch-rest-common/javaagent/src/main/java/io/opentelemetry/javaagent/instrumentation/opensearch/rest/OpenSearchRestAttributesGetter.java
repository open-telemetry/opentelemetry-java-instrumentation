/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.OPENSEARCH;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest> {

  @Deprecated
  @Override
  public String getSystem(OpenSearchRestRequest request) {
    return OPENSEARCH;
  }

  @Override
  public String getDbSystem(OpenSearchRestRequest openSearchRestRequest) {
    return OPENSEARCH;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(OpenSearchRestRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getName(OpenSearchRestRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(OpenSearchRestRequest openSearchRestRequest) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(OpenSearchRestRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getStatement(OpenSearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  public String getDbQueryText(OpenSearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Deprecated
  @Override
  @Nullable
  public String getOperation(OpenSearchRestRequest request) {
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getDbOperationName(OpenSearchRestRequest request) {
    return request.getMethod();
  }
}
