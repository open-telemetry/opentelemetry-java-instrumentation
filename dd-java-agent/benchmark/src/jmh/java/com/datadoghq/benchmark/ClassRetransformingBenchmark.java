package com.datadoghq.benchmark;

import com.datadoghq.benchmark.classes.DeepClass;
import com.datadoghq.benchmark.classes.SimpleClass;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class ClassRetransformingBenchmark {
  public static final String BENCHMARK_HOME =
      Paths.get(".").toAbsolutePath().normalize().toString();

  static {
    if (!BENCHMARK_HOME.endsWith("benchmark")) {
      throw new IllegalArgumentException("Invalid Home directory: " + BENCHMARK_HOME);
    }
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private final Instrumentation inst = ByteBuddyAgent.install();

    @Setup
    public void initializeInstrumentation() {
      try {
        final Class<?> manager = Class.forName("com.datadoghq.agent.InstrumentationRulesManager");
        final Method registerClassLoad = manager.getMethod("registerClassLoad");
        registerClassLoad.invoke(null);
      } catch (final Exception e) {
      }
    }

    @TearDown
    public void stopAgent() {
      try {
        final Class<?> gt = Class.forName("io.opentracing.util.GlobalTracer");
        final Field tracerField = gt.getDeclaredField("tracer");
        tracerField.setAccessible(true);
        final Object tracer = tracerField.get(null);
        final Method close = tracer.getClass().getMethod("close");
        close.invoke(tracer);
      } catch (final Exception e) {
      }
    }
  }

  @Benchmark
  public void testSimpleRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(SimpleClass.class);
  }

  @Benchmark
  public void testDeepRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(DeepClass.class);
  }

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.2.jar")
  public static class WithAgent022 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.4.jar")
  public static class WithAgent024 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.6.jar")
  public static class WithAgent026 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.7.jar")
  public static class WithAgent027 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.8.jar")
  public static class WithAgent028 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.9.jar")
  public static class WithAgent029 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.10.jar")
  public static class WithAgent0210 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:../build/libs/dd-java-agent.jar")
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
