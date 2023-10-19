/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
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
      Instrumenter.<Void, Void>builder(
              OpenTelemetry.noop(),
              "benchmark",
              HttpSpanNameExtractor.create(ConstantHttpAttributesGetter.INSTANCE))
          .addAttributesExtractor(
              HttpClientAttributesExtractor.create(ConstantHttpAttributesGetter.INSTANCE))
          .buildInstrumenter();

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

  enum ConstantHttpAttributesGetter implements HttpClientAttributesGetter<Void, Void> {
    INSTANCE;

    private static final InetSocketAddress PEER_ADDRESS =
        InetSocketAddress.createUnresolved("localhost", 8080);

    @Override
    public String getUrlFull(Void unused) {
      return "https://opentelemetry.io/benchmark";
    }

    @Override
    public String getHttpRequestMethod(Void unused) {
      return "GET";
    }

    @Override
    public List<String> getHttpRequestHeader(Void unused, String name) {
      if (name.equalsIgnoreCase("user-agent")) {
        return Collections.singletonList("OpenTelemetryBot");
      }
      return Collections.emptyList();
    }

    @Override
    public Integer getHttpResponseStatusCode(Void unused, Void unused2, @Nullable Throwable error) {
      return 200;
    }

    @Override
    public List<String> getHttpResponseHeader(Void unused, Void unused2, String name) {
      return Collections.emptyList();
    }

    @Override
    public String getNetworkProtocolName(Void unused, @Nullable Void unused2) {
      return "http";
    }

    @Override
    public String getNetworkProtocolVersion(Void unused, @Nullable Void unused2) {
      return "2.0";
    }

    @Nullable
    @Override
    public String getServerAddress(Void request) {
      return null;
    }

    @Nullable
    @Override
    public Integer getServerPort(Void request) {
      return null;
    }

    @Override
    public InetSocketAddress getNetworkPeerInetSocketAddress(
        Void request, @Nullable Void response) {
      return PEER_ADDRESS;
    }
  }
}
