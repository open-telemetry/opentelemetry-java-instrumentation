/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import com.couchbase.client.core.msg.RequestContext;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseSpan;

public final class CouchbaseConfiguredTarget {

  public static void capture(
      CouchbaseSpan span, CouchbaseSpanName spanName, RequestContext requestContext) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    CouchbaseServerTarget target = CouchbaseServerTargets.get(requestContext.core());
    spanName.captureServerTarget(target);
    if (target == null) {
      return;
    }
    span.setRawAttribute(SERVER_ADDRESS.getKey(), target.getAddress());
    Integer port = target.getPort();
    if (port != null) {
      span.setRawAttribute(SERVER_PORT.getKey(), port.longValue());
    }
  }

  private CouchbaseConfiguredTarget() {}
}
