/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceReactiveCommandsInstrumentation extends Instrumenter.Default {

  public LettuceReactiveCommandsInstrumentation() {
    super("lettuce", "lettuce-5", "lettuce-5-rx");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("io.lettuce.core.tracing.Tracing"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisReactiveCommands");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.lettuce.LettuceArgSplitter",
      packageName + ".LettuceAbstractDatabaseClientTracer",
      packageName + ".LettuceConnectionDatabaseClientTracer",
      packageName + ".LettuceDatabaseClientTracer",
      packageName + ".LettuceInstrumentationUtil",
      packageName + ".rx.LettuceMonoCreationAdvice",
      packageName + ".rx.LettuceMonoDualConsumer",
      packageName + ".rx.LettuceFluxCreationAdvice",
      packageName + ".rx.LettuceFluxTerminationRunnable",
      packageName + ".rx.LettuceFluxTerminationRunnable$FluxOnSubscribeConsumer"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("createMono"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        packageName + ".rx.LettuceMonoCreationAdvice");
    transformers.put(
        isMethod()
            .and(nameStartsWith("create"))
            .and(nameEndsWith("Flux"))
            .and(isPublic())
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("reactor.core.publisher.Flux"))),
        packageName + ".rx.LettuceFluxCreationAdvice");

    return transformers;
  }
}
