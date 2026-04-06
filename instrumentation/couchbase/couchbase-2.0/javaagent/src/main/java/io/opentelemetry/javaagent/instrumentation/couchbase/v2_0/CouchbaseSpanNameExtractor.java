/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CouchbaseSpanNameExtractor implements SpanNameExtractor<CouchbaseRequestInfo> {
  private final SpanNameExtractor<CouchbaseRequestInfo> dbSpanNameExtractor;

  public CouchbaseSpanNameExtractor(SpanNameExtractor<CouchbaseRequestInfo> dbSpanNameExtractor) {
    this.dbSpanNameExtractor = dbSpanNameExtractor;
  }

  @Override
  public String extract(CouchbaseRequestInfo couchbaseRequest) {
    if (couchbaseRequest.isMethodCall()) {
      return couchbaseRequest.operation();
    }
    return dbSpanNameExtractor.extract(couchbaseRequest);
  }
}
