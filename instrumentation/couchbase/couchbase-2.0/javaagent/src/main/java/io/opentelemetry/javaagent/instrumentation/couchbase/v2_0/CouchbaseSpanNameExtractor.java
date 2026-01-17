/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;

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
    
    // In stable semconv mode, use query summary for span name if available
    if (SemconvStability.emitStableDatabaseSemconv()) {
      String querySummary = couchbaseRequest.querySummary();
      if (querySummary != null) {
        return querySummary;
      }
    }
    
    return dbSpanNameExtractor.extract(couchbaseRequest);
  }
}
