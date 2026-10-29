/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.bridgecost;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 10)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
public class BridgeCost {
  Tracer tracer;
  Context parent;
  Span child;
  Context withChild;

  @Setup
  public void setup() {
    /*
    To run without the agent add the sdk dependency to build.gradle.kts
    jmhImplementation("io.opentelemetry:opentelemetry-sdk")
    also comment out adding the -javaagent: flag to jvm. Use the following to set up the global
    OpenTelemetry instance.

    OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build())
        .buildAndRegisterGlobal();
     */

    tracer = GlobalOpenTelemetry.get().getTracer("bridge-cost");
    parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        "0af7651916cd43dd8448eb211c80319c",
                        "b7ad6b7169203331",
                        TraceFlags.getDefault(),
                        TraceState.getDefault())));
    child = tracer.spanBuilder("child").setParent(parent).startSpan();
    withChild = parent.with(child);
    System.out.println(
        "child span: " + child.getClass().getName() + ", recording " + child.isRecording());
  }

  @Benchmark
  public Span startAndEndChildOfUnsampledParent() {
    Span span = tracer.spanBuilder("child").setParent(parent).startSpan();
    span.end();
    return span;
  }

  @Benchmark
  public SpanContext getSpanContext() {
    return child.getSpanContext();
  }

  @Benchmark
  public SpanContext spanFromContextGetSpanContext() {
    return Span.fromContext(withChild).getSpanContext();
  }

  @Benchmark
  public Context contextWithSpan() {
    return parent.with(child);
  }

  @Benchmark
  public Scope spanMakeCurrentAndClose() {
    Scope scope = child.makeCurrent();
    scope.close();
    return scope;
  }

  @Benchmark
  public Scope contextMakeCurrentAndClose() {
    Scope scope = withChild.makeCurrent();
    scope.close();
    return scope;
  }
}
