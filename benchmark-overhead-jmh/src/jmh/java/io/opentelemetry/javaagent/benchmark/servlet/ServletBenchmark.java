/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import io.opentelemetry.javaagent.benchmark.servlet.app.HelloWorldApplication;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ServletBenchmark {

  static {
    HelloWorldApplication.main();
  }

  // using shaded armeria http client from testing-common artifact since it won't be instrumented
  private WebClient client;

  @Setup
  public void setup() {
    client = WebClient.builder().build();
  }

  @TearDown
  public void tearDown() {
    HelloWorldApplication.stop();
  }

  @Benchmark
  public Object execute() {
    return client.get("http://localhost:8080").aggregate().join();
  }
}
