/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.InvocationResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Stands in for the worker class of the same name. The instrumentation matches the worker type by
 * name, so this class has to keep both its name and the signature of {@code execute}. The real
 * method is package private, it is public here so that the test can call it.
 *
 * <p>See
 * https://github.com/Azure/azure-functions-java-worker/blob/dev/src/main/java/com/microsoft/azure/functions/worker/handler/InvocationRequestHandler.java
 */
public class InvocationRequestHandler {

  private SpanContext capturedSpanContext = SpanContext.getInvalid();

  public SpanContext getCapturedSpanContext() {
    return capturedSpanContext;
  }

  public String execute(InvocationRequest request, InvocationResponse.Builder response) {
    capturedSpanContext = Span.current().getSpanContext();
    // stands in for the function invocation, this is what has to end up correlated with the
    // incoming request
    GlobalOpenTelemetry.getTracer("test").spanBuilder("function").startSpan().end();
    return "invoked";
  }
}
