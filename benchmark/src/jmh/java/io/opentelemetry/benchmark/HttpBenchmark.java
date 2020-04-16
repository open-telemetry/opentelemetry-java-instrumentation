package io.opentelemetry.benchmark;

import io.opentelemetry.benchmark.classes.HttpClass;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.openjdk.jmh.annotations.*;

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
  public void testMakingRequest(BenchmarkState state) {
    state.http.executeRequest();
  }

  @Fork(
      jvmArgsAppend = {
        "-javaagent:/path/to/opentelemetry-auto-instr-java/java-agent/build/libs/opentelemetry-auto.jar",
        "-javaagent:/path/to/opentelemetry-auto-instr-java/java-agent/build/libs/opentelemetry-auto.jar",
        "-Dota.exporter.jar=/path/to/opentelemetry-auto-instr-java/auto-exporters/logging/build/libs/opentelemetry-auto-exporters-logging-or-other-exporter.jar"
      })
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
