/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import io.opentelemetry.javaagent.benchmark.servlet.app.HelloWorldApplication;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    // using static initializer instead of @Setup since only want to initialize the app under test
    // once regardless of @State and @Threads
    HelloWorldApplication.main();
  }

  private URL client;
  private byte[] buffer;

  @Setup
  public void setup() throws IOException {
    client = new URL("http://localhost:8080");
    buffer = new byte[8192];
  }

  @TearDown
  public void tearDown() {
    HelloWorldApplication.stop();
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
