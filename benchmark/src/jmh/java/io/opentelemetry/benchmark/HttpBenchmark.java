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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpBenchmark {

  private static final Logger logger = LoggerFactory.getLogger(HttpBenchmark.class);

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
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    @TearDown(Level.Trial)
    public void doTearDown() {
      try {
        jettyServer.stop();
      } catch (Exception e) {
        logger.warn("Error", e);
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
