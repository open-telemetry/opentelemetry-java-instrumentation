/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CouchbaseAttributesExtractor extends DbAttributesExtractor<CouchbaseRequest, Void> {
  @Override
  protected String system(CouchbaseRequest couchbaseRequest) {
    return SemanticAttributes.DbSystemValues.COUCHBASE;
  }

  @Override
  protected @Nullable String user(CouchbaseRequest couchbaseRequest) {
    return null;
  }

  @Override
  protected @Nullable String name(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  protected @Nullable String connectionString(CouchbaseRequest couchbaseRequest) {
    return null;
  }

  @Override
  protected @Nullable String statement(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  protected @Nullable String operation(CouchbaseRequest couchbaseRequest) {
    return couchbaseRequest.operation();
  }
}
