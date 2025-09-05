/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
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

  private static final Object REQUEST = new Object();

  private static final Instrumenter<Object, Void> INSTRUMENTER =
      Instrumenter.<Object, Void>builder(
              OpenTelemetry.noop(),
              "benchmark",
              HttpSpanNameExtractor.create(ConstantHttpAttributesGetter.INSTANCE))
          .addAttributesExtractor(
              HttpClientAttributesExtractor.create(ConstantHttpAttributesGetter.INSTANCE))
          .buildInstrumenter();

  @Benchmark
  public Context start() {
    return INSTRUMENTER.start(Context.root(), REQUEST);
  }

  @Benchmark
  public Context startEnd() {
    Context context = INSTRUMENTER.start(Context.root(), REQUEST);
    INSTRUMENTER.end(context, REQUEST, null, null);
    return context;
  }

  enum ConstantHttpAttributesGetter implements HttpClientAttributesGetter<Object, Void> {
    INSTANCE;

    private static final InetSocketAddress PEER_ADDRESS =
        InetSocketAddress.createUnresolved("localhost", 8080);

    @Override
    public String getUrlFull(Object unused) {
      return "https://opentelemetry.io/benchmark";
    }

    @Override
    public String getHttpRequestMethod(Object unused) {
      return "GET";
    }

    @Override
    public List<String> getHttpRequestHeader(Object unused, String name) {
      if (name.equalsIgnoreCase("user-agent")) {
        return Collections.singletonList("OpenTelemetryBot");
      }
      return Collections.emptyList();
    }

    @Override
    public Integer getHttpResponseStatusCode(
        Object unused, Void unused2, @Nullable Throwable error) {
      return 200;
    }

    @Override
    public List<String> getHttpResponseHeader(Object unused, Void unused2, String name) {
      return Collections.emptyList();
    }

    @Override
    public String getNetworkProtocolName(Object unused, @Nullable Void unused2) {
      return "http";
    }

    @Override
    public String getNetworkProtocolVersion(Object unused, @Nullable Void unused2) {
      return "2.0";
    }

    @Nullable
    @Override
    public String getServerAddress(Object request) {
      return null;
    }

    @Nullable
    @Override
    public Integer getServerPort(Object request) {
      return null;
    }

    @Override
    public InetSocketAddress getNetworkPeerInetSocketAddress(
        Object request, @Nullable Void response) {
      return PEER_ADDRESS;
    }
  }
}
