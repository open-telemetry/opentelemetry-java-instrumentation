/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CouchbaseAttributesExtractor extends DbAttributesExtractor<CouchbaseRequestInfo, Void> {
  @Override
  protected String system(CouchbaseRequestInfo couchbaseRequest) {
    return SemanticAttributes.DbSystemValues.COUCHBASE;
  }

  @Override
  @Nullable
  protected String user(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String name(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  protected String connectionString(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  @Nullable
  protected String operation(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.operation();
  }
}
