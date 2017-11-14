package com.datadoghq.agent;

import com.datadoghq.trace.Trace;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class ClassRetransformingBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private final Instrumentation inst = ByteBuddyAgent.install();
  }

  @Benchmark
  public void testIgnoredRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(Object.class);
  }

  @Benchmark
  public void testSimpleRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(SimpleClass.class);
  }

  @Benchmark
  public void testDeepRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(DeepClass.class);
  }

  public static class SimpleClass {
    @Trace
    public void aMethodToTrace() {}
  }

  public static interface A {
    @Trace
    void interfaceTrace();
  }

  public static interface B extends A {
    void something();
  }

  public static interface C extends B {
    void somethingElse();
  }

  public static class DeepClass implements C {

    @Override
    public void interfaceTrace() {}

    @Override
    public void something() {}

    @Override
    public void somethingElse() {}
  }
}
