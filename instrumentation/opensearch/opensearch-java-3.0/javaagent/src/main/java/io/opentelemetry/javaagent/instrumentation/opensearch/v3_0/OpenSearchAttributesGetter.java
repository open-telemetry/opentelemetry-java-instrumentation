/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class OpenSearchAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRequest, Void> {

  @Override
  public String getDbSystemName(OpenSearchRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
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
      // not available for some reason
      return request.getMethod() + " " + request.getEndpoint();
    }
    return request.getBody();
  }

  @Override
  @Nullable
  public String getDbOperationName(OpenSearchRequest request) {
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(@Nullable Void response, @Nullable Throwable error) {
    return null; // Response status is handled by HTTP instrumentation
  }
}
