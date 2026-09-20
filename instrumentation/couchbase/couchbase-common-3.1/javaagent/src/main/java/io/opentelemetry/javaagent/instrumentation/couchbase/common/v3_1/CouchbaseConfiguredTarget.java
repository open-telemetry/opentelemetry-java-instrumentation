/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import com.couchbase.client.core.msg.Request;
import com.couchbase.client.core.msg.RequestContext;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseSpan;
import javax.annotation.Nullable;

public final class CouchbaseConfiguredTarget {

  private static final VirtualField<Request<?>, CouchbaseServerTarget> REQUEST_TARGETS =
      VirtualField.find(Request.class, CouchbaseServerTarget.class);

  public static void registerRequestTarget(
      Request<?> request, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      REQUEST_TARGETS.set(request, target);
    }
  }

  public static void capture(
      CouchbaseSpan span, CouchbaseSpanName spanName, RequestContext requestContext) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    CouchbaseServerTarget target = CouchbaseServerTargets.get(requestContext.core());
    if (target == null) {
      target = REQUEST_TARGETS.get(requestContext.request());
    }
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
