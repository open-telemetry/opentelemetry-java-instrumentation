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

  @Override
  public String getDbSystem(OpenSearchRestRequest request) {
    return OPENSEARCH;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchRestRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(OpenSearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  @Nullable
  public String getDbOperationName(OpenSearchRestRequest request) {
    return request.getMethod();
  }
}
