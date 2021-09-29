/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class InstrumenterBenchmark {

  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>newBuilder(
              OpenTelemetry.noop(),
              "benchmark",
              HttpSpanNameExtractor.create(ConstantHttpAttributesExtractor.INSTANCE))
          .addAttributesExtractor(ConstantHttpAttributesExtractor.INSTANCE)
          .addAttributesExtractor(new ConstantNetAttributesExtractor())
          .newInstrumenter();

  @Benchmark
  public Context start() {
    return INSTRUMENTER.start(Context.root(), null);
  }

  @Benchmark
  public Context startEnd() {
    Context context = INSTRUMENTER.start(Context.root(), null);
    INSTRUMENTER.end(context, null, null, null);
    return context;
  }

  static class ConstantHttpAttributesExtractor extends HttpClientAttributesExtractor<Void, Void> {
    static final HttpClientAttributesExtractor<Void, Void> INSTANCE =
        new ConstantHttpAttributesExtractor();

    public ConstantHttpAttributesExtractor() {
      super(CapturedHttpHeaders.empty());
    }

    @Override
    protected @Nullable String method(Void unused) {
      return "GET";
    }

    @Override
    protected @Nullable String url(Void unused) {
      return "https://opentelemetry.io/benchmark";
    }

    @Override
    protected @Nullable String userAgent(Void unused) {
      return "OpenTelemetryBot";
    }

    @Override
    protected List<String> requestHeader(Void unused, String name) {
      return Collections.emptyList();
    }

    @Override
    protected @Nullable Long requestContentLength(Void unused, @Nullable Void unused2) {
      return 100L;
    }

    @Override
    protected @Nullable Long requestContentLengthUncompressed(Void unused, @Nullable Void unused2) {
      return null;
    }

    @Override
    protected @Nullable String flavor(Void unused, @Nullable Void unused2) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }

    @Override
    protected @Nullable Integer statusCode(Void unused, Void unused2) {
      return 200;
    }

    @Override
    protected @Nullable Long responseContentLength(Void unused, Void unused2) {
      return 100L;
    }

    @Override
    protected @Nullable Long responseContentLengthUncompressed(Void unused, Void unused2) {
      return null;
    }

    @Override
    protected List<String> responseHeader(Void unused, Void unused2, String name) {
      return Collections.emptyList();
    }
  }

  static class ConstantNetAttributesExtractor
      extends InetSocketAddressNetAttributesExtractor<Void, Void> {

    private static final InetSocketAddress ADDRESS =
        InetSocketAddress.createUnresolved("localhost", 8080);

    @Override
    public @Nullable InetSocketAddress getAddress(Void unused, @Nullable Void unused2) {
      return ADDRESS;
    }

    @Override
    public @Nullable String transport(Void unused) {
      return SemanticAttributes.NetTransportValues.IP_TCP;
    }
  }
}
