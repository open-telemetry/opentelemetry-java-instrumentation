/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark;

import io.opentelemetry.benchmark.classes.TracedClass;
import io.opentelemetry.benchmark.classes.UntracedClass;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class ClassRetransformingBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private final Instrumentation inst = ByteBuddyAgent.install();
  }

  @Benchmark
  public void testUntracedRetransform(BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(UntracedClass.class);
  }

  @Benchmark
  public void testTracedRetransform(BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(TracedClass.class);
  }

  @Fork(
      jvmArgsAppend =
          "-javaagent:/path/to/opentelemetry-java-instrumentation"
              + "/javaagent/build/libs/opentelemetry-javaagent.jar")
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
