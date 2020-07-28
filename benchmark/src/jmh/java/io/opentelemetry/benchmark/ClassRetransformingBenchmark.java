/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  public void testUntracedRetransform(final BenchmarkState state)
      throws UnmodifiableClassException {
    state.inst.retransformClasses(UntracedClass.class);
  }

  @Benchmark
  public void testTracedRetransform(final BenchmarkState state) throws UnmodifiableClassException {
    state.inst.retransformClasses(TracedClass.class);
  }

  @Fork(
      jvmArgsAppend =
          "-javaagent:/path/to/opentelemetry-java-instrumentation"
              + "/opentelemetry-javaagent/build/libs/opentelemetry-javaagent.jar")
  public static class WithAgent extends ClassRetransformingBenchmark {}
}
