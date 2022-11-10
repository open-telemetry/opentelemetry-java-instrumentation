/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest> {

  @Override
  public String system(OpenSearchRestRequest request) {
    return SemanticAttributes.DbSystemValues.OPENSEARCH;
  }

  @Override
  @Nullable
  public String user(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String name(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String statement(OpenSearchRestRequest request) {
    return request.getMethod() + " " + request.getOperation();
  }

  @Override
  @Nullable
  public String operation(OpenSearchRestRequest request) {
    return request.getMethod();
  }
}
