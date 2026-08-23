/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;

class CouchbaseSpanNameExtractor implements SpanNameExtractor<CouchbaseRequestInfo> {
  private final SpanNameExtractor<CouchbaseRequestInfo> dbSpanNameExtractor;

  CouchbaseSpanNameExtractor(SpanNameExtractor<CouchbaseRequestInfo> dbSpanNameExtractor) {
    this.dbSpanNameExtractor = dbSpanNameExtractor;
  }

  @Override
  public String extract(CouchbaseRequestInfo couchbaseRequest) {
    if (!emitStableDatabaseSemconv() && couchbaseRequest.isMethodCall()) {
      return couchbaseRequest.getOperation();
    }
    return dbSpanNameExtractor.extract(couchbaseRequest);
  }
}
