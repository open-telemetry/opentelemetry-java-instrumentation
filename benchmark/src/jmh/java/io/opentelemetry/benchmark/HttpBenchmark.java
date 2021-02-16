/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark;

import io.opentelemetry.benchmark.classes.HttpClass;
import java.io.IOException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class HttpBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    @Setup(Level.Trial)
    public void doSetup() {
      try {
        jettyServer = new HttpClass().buildJettyServer();
        jettyServer.start();
        // Make sure it's actually running
        while (!AbstractLifeCycle.STARTED.equals(jettyServer.getState())) {
          Thread.sleep(500);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @TearDown(Level.Trial)
    public void doTearDown() {
      try {
        jettyServer.stop();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        jettyServer.destroy();
      }
    }

    HttpClass http = new HttpClass();
    Server jettyServer;
  }

  @Benchmark
  public void testMakingRequest(BenchmarkState state) throws IOException {
    state.http.executeRequest();
  }

  @Fork(
      jvmArgsAppend = {
        "-javaagent:/path/to/opentelemetry-java-instrumentation/java-agent/build/libs/opentelemetry-javaagent.jar",
        "-Dotel.traces.exporter=logging"
      })
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
