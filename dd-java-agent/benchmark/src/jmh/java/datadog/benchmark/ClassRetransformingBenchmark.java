package datadog.benchmark;

import datadog.benchmark.classes.TracedClass;
import datadog.benchmark.classes.UntracedClass;
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
      // loading TracedClass will initialize helper injection
      TracedClass.class.getName();
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
  public void testUntracedRetransform(final BenchmarkState state)
      throws UnmodifiableClassException {
    state.inst.retransformClasses(UntracedClass.class);
  }

  @Benchmark
  public void testTracedRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(TracedClass.class);
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

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.11.jar")
  public static class WithAgent0211 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.2.12.jar")
  public static class WithAgent0212 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.3.0.jar")
  public static class WithAgent030 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.3.1.jar")
  public static class WithAgent031 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.3.2.jar")
  public static class WithAgent032 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.3.3.jar")
  public static class WithAgent033 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.4.0.jar")
  public static class WithAgent040 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.4.1.jar")
  public static class WithAgent041 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:releases/dd-java-agent-0.5.0.jar")
  public static class WithAgent050 extends ClassRetransformingBenchmark {}

  @Fork(jvmArgsAppend = "-javaagent:../build/libs/dd-java-agent.jar")
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
