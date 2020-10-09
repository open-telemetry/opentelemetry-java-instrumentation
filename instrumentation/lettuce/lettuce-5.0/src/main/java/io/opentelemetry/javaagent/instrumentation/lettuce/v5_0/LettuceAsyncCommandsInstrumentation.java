/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceAsyncCommandsInstrumentation extends Instrumenter.Default {

  public LettuceAsyncCommandsInstrumentation() {
    super("lettuce", "lettuce-5", "lettuce-5-async");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("io.lettuce.core.tracing.Tracing"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisAsyncCommands");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".LettuceAbstractDatabaseClientTracer",
      packageName + ".LettuceConnectionDatabaseClientTracer",
      packageName + ".LettuceDatabaseClientTracer",
      packageName + ".LettuceAsyncBiFunction",
      packageName + ".LettuceInstrumentationUtil"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        packageName + ".LettuceAsyncCommandsAdvice");
  }
}
