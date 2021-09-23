/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CouchbaseSpanNameExtractor implements SpanNameExtractor<CouchbaseRequest> {
  private final SpanNameExtractor<CouchbaseRequest> dbSpanNameExtractor;

  public CouchbaseSpanNameExtractor(SpanNameExtractor<CouchbaseRequest> dbSpanNameExtractor) {
    this.dbSpanNameExtractor = dbSpanNameExtractor;
  }

  @Override
  public String extract(CouchbaseRequest couchbaseRequest) {
    if (couchbaseRequest.isMethodCall()) {
      return couchbaseRequest.operation();
    }
    return dbSpanNameExtractor.extract(couchbaseRequest);
  }
}
