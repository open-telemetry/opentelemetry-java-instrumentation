/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter implements DbClientAttributesGetter<CouchbaseRequestInfo> {

  @Override
  public String system(CouchbaseRequestInfo couchbaseRequest) {
    return SemanticAttributes.DbSystemValues.COUCHBASE;
  }

  @Override
  @Nullable
  public String user(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String name(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  public String connectionString(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String statement(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  @Nullable
  public String operation(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.operation();
  }
}
