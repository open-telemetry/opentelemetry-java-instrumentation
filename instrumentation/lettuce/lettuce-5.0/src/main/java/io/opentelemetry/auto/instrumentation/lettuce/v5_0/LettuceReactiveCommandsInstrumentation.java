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

package io.opentelemetry.auto.instrumentation.lettuce.v5_0;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
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
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
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
