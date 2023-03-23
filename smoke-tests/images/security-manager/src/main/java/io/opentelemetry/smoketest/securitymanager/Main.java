/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.securitymanager;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class Main {

  private Main() {}

  @SuppressWarnings("SystemOut")
  public static void main(String[] args) {
    Tracer tracer = GlobalOpenTelemetry.get().getTracer("test-tracer");
    Span span = tracer.spanBuilder("test").startSpan();
    try (Scope ignore = span.makeCurrent()) {
    } finally {
      span.end();
    }
    System.out.println("completed successfully");
  }
}
