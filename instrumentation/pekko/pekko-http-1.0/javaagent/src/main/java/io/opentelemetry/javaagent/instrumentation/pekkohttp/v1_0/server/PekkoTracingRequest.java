/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import org.apache.pekko.http.scaladsl.model.AttributeKey;
import org.apache.pekko.http.scaladsl.model.HttpRequest;

public class PekkoTracingRequest {
  static final AttributeKey<PekkoTracingRequest> ATTR_KEY =
      new AttributeKey<>("_otel_ctx", PekkoTracingRequest.class);
  static final PekkoTracingRequest EMPTY = new PekkoTracingRequest(null, null, null);
  final Context context;
  final HttpRequest request;
  final PekkoRouteHolder initialRouteHolder;

  PekkoTracingRequest(Context context, HttpRequest request, PekkoRouteHolder initialRouteHolder) {
    this.context = context;
    this.request = request;
    this.initialRouteHolder = initialRouteHolder;
  }
}
