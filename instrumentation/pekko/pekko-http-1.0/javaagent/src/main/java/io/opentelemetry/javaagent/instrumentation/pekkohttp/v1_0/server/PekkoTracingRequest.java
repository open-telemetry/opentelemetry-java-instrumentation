/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import org.apache.pekko.http.scaladsl.model.AttributeKey;
import org.apache.pekko.http.scaladsl.model.HttpRequest;

public class PekkoTracingRequest implements ImplicitContextKeyed {
  private static final ContextKey<PekkoTracingRequest> CONTEXT_KEY = named("opentelemetry-pekko-tracing-request");
  static final AttributeKey<PekkoTracingRequest> ATTR_KEY =
      new AttributeKey<>("_otel_ctx", PekkoTracingRequest.class);
  static final PekkoTracingRequest EMPTY = new PekkoTracingRequest(null, null, null);
  final Context context;
  final Context parentContext;
  final HttpRequest request;

  PekkoTracingRequest(Context context, Context parentContext, HttpRequest request) {
    this.context = context;
    this.parentContext = parentContext;
    this.request = request;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(CONTEXT_KEY, this);
  }
}
