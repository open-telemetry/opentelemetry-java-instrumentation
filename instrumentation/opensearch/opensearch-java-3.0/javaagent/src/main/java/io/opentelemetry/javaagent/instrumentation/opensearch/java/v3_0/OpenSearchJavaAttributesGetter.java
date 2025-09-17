/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbResponseStatusUtil.dbResponseStatusCode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class OpenSearchJavaAttributesGetter
    implements DbClientAttributesGetter<OpenSearchJavaRequest, OpenSearchJavaResponse> {

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(OpenSearchJavaRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.OPENSEARCH;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(OpenSearchJavaRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchJavaRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(OpenSearchJavaRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(OpenSearchJavaRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  @Nullable
  public String getDbOperationName(OpenSearchJavaRequest request) {
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getResponseStatus(
      @Nullable OpenSearchJavaResponse response, @Nullable Throwable error) {
    return response != null ? dbResponseStatusCode(response.getStatusCode()) : null;
  }
}
