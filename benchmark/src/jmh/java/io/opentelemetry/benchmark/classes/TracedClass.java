/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark.classes;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public class TracedClass extends UntracedClass {
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  @Override
  public void f() {
    tracer.spanBuilder("f").startSpan().end();
  }

  @Override
  public void e() {
    tracer.spanBuilder("e").startSpan().end();
  }

  @Override
  public void d() {
    tracer.spanBuilder("d").startSpan().end();
  }

  @Override
  public void c() {
    tracer.spanBuilder("c").startSpan().end();
  }

  @Override
  public void b() {
    tracer.spanBuilder("b").startSpan().end();
  }

  @Override
  public void a() {
    tracer.spanBuilder("a").startSpan().end();
  }
}
