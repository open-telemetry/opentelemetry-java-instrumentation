/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.benchmark.servlet.app.HelloWorldApplication;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(MICROSECONDS)
@State(Scope.Thread)
public class ServletBenchmark {

  private URL client;
  private byte[] buffer;

  @Setup
  public void setup(ApplicationState application) throws IOException {
    client = new URL("http://localhost:8080");
    buffer = new byte[8192];
    HttpURLConnection connection = (HttpURLConnection) client.openConnection();
    try (InputStream inputStream = connection.getInputStream()) {
      String response = new String(inputStream.readAllBytes(), UTF_8);
      if (!response.equals("Hello world!")) {
        throw new IllegalStateException("Unexpected servlet response: " + response);
      }
    } finally {
      connection.disconnect();
    }
  }

  @State(Scope.Benchmark)
  public static class ApplicationState {

    @Setup
    public void start() {
      if (Boolean.getBoolean("benchmark.verify-tracing")) {
        verifyTracing();
      }
      HelloWorldApplication.main();
    }

    private static void verifyTracing() {
      String sampler = System.getProperty("otel.traces.sampler", "parentbased_always_on");
      boolean enabled =
          !Boolean.getBoolean("otel.sdk.disabled")
              && !System.getProperty("otel.javaagent.enabled", "true").equals("false");
      Span root =
          GlobalOpenTelemetry.getTracer("servlet-benchmark-preflight")
              .spanBuilder("verify-root")
              .setNoParent()
              .startSpan();
      try {
        if (root.getSpanContext().isValid() != enabled) {
          throw new IllegalStateException("Unexpected SDK tracing state for a root span");
        }
      } finally {
        root.end();
      }
      if (enabled) {
        verifySpan("00000000000000000000000000000001", !sampler.equals("always_off"));
        verifySpan(
            "7fffffffffffffff7fffffffffffffff",
            !sampler.equals("always_off") && !sampler.equals("traceidratio"));
      }
      System.out.println("Verified active tracing and sampler " + sampler);
    }

    private static void verifySpan(String traceId, boolean recording) {
      SpanContext parent =
          SpanContext.create(
              traceId, "0000000000000001", TraceFlags.getSampled(), TraceState.getDefault());
      Span span =
          GlobalOpenTelemetry.getTracer("servlet-benchmark-preflight")
              .spanBuilder("verify-sampling")
              .setParent(Context.root().with(Span.wrap(parent)))
              .startSpan();
      try {
        if (!span.getSpanContext().isValid() || span.isRecording() != recording) {
          throw new IllegalStateException(
              "Unexpected SDK tracing state: valid="
                  + span.getSpanContext().isValid()
                  + ", recording="
                  + span.isRecording());
        }
      } finally {
        span.end();
      }
    }

    @TearDown
    public void stop() {
      HelloWorldApplication.stop();
    }
  }

  @Benchmark
  public void execute() throws IOException {
    HttpURLConnection connection = (HttpURLConnection) client.openConnection();
    InputStream inputStream = connection.getInputStream();
    drain(inputStream);
    inputStream.close();
    connection.disconnect();
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private void drain(InputStream inputStream) throws IOException {
    while (inputStream.read(buffer) != -1) {}
  }
}
