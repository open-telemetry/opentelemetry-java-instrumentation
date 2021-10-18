/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CouchbaseAttributesExtractor extends DbAttributesExtractor<CouchbaseRequest, Void> {
  @Override
  protected String system(CouchbaseRequest couchbaseRequest) {
    return SemanticAttributes.DbSystemValues.COUCHBASE;
  }

  @Override
  @Nullable
  protected String user(CouchbaseRequest couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String name(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  protected String connectionString(CouchbaseRequest couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  @Nullable
  protected String operation(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.operation();
  }
}
