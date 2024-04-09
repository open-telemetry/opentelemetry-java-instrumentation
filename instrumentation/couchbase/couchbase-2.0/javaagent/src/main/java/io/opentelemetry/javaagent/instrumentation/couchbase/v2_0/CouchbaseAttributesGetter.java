/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter implements DbClientAttributesGetter<CouchbaseRequestInfo> {

  @Override
  public String getSystem(CouchbaseRequestInfo couchbaseRequest) {
    return DbIncubatingAttributes.DbSystemValues.COUCHBASE;
  }

  @Override
  @Nullable
  public String getUser(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getName(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  public String getConnectionString(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getStatement(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  @Nullable
  public String getOperation(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.operation();
  }
}
