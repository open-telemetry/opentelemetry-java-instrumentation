/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import javax.ws.rs.container.ContainerRequestContext;

public final class RequestContextHelper {
  public static Context createOrUpdateAbortSpan(
      ContainerRequestContext requestContext, HandlerData handlerData) {

    if (handlerData == null) {
      return null;
    }

    requestContext.setProperty(JaxrsSingletons.ABORT_HANDLED, true);
    Context parentContext = Java8BytecodeBridge.currentContext();
    Span serverSpan = ServerSpan.fromContextOrNull(parentContext);
    Span currentSpan = Java8BytecodeBridge.spanFromContext(parentContext);

    ServerSpanNaming.updateServerSpanName(
        parentContext,
        ServerSpanNaming.Source.CONTROLLER,
        JaxrsServerSpanNaming.getServerSpanNameSupplier(parentContext, handlerData));

    // if there's no current span or it's the same as the server (servlet) span we need to start
    // a JAX-RS one
    // in other case, DefaultRequestContextInstrumentation must have already run so it's enough
    // to just update the names
    if (currentSpan != null && currentSpan != serverSpan) {
      return null;
    }

    if (!instrumenter().shouldStart(parentContext, handlerData)) {
      return null;
    }

    return instrumenter().start(parentContext, handlerData);
  }

  private RequestContextHelper() {}
}
