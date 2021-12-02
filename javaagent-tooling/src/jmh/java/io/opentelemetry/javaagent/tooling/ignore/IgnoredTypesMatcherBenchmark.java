/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.ignore.AdditionalLibraryIgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesBuilderImpl;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesMatcher;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class IgnoredTypesMatcherBenchmark {

  private static final TypeDescription springType =
      new TypeDescription.Latent("org.springframework.test.SomeClass", 0, null);
  private static final TypeDescription testAppType =
      new TypeDescription.Latent("com.example.myapp.Main", 0, null);

  private static final ElementMatcher<TypeDescription> ignoredTypesMatcher;

  static {
    IgnoredTypesBuilderImpl builder = new IgnoredTypesBuilderImpl();
    new AdditionalLibraryIgnoredTypesConfigurer().configure(Config.get(), builder);
    ignoredTypesMatcher = new IgnoredTypesMatcher(builder.buildIgnoredTypesTrie());
  }

  @Benchmark
  public boolean springType() {
    return ignoredTypesMatcher.matches(springType);
  }

  @Benchmark
  public boolean appType() {
    return ignoredTypesMatcher.matches(testAppType);
  }
}
