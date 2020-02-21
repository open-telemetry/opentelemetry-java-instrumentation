package datadog.benchmark;

import datadog.benchmark.classes.TracedClass;
import datadog.benchmark.classes.UntracedClass;
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
  public void testUntracedRetransform(final BenchmarkState state)
      throws UnmodifiableClassException {
    state.inst.retransformClasses(UntracedClass.class);
  }

  @Benchmark
  public void testTracedRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(TracedClass.class);
  }

  @Fork(jvmArgsAppend = "-javaagent:/path/to/dd-java-agent-master.jar")
  public static class WithAgentMaster extends ClassRetransformingBenchmark {}

  @Fork(
      jvmArgsAppend =
          "-javaagent:/path/to/dd-trace-java/dd-java-agent/build/libs/dd-java-agent.jar")
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
