/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

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
      HelloWorldApplication.main();
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
