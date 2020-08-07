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

package io.opentelemetry.auto.instrumentation.armeria.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.auto.instrumentation.armeria.v1_0.shaded.server.OpenTelemetryService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ArmeriaServerBuilderInstrumentation extends AbstractArmeriaInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.linecorp.armeria.server.ServerBuilder");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isConstructor(),
        ArmeriaServerBuilderInstrumentation.class.getName() + "$ConstructorAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("decorator").and(takesArgument(0, Function.class))),
        ArmeriaServerBuilderInstrumentation.class.getName() + "$SuppressDecoratorAdvice");
    return transformers;
  }

  public static class ConstructorAdvice {
    @Advice.OnMethodExit
    public static void construct(@Advice.This ServerBuilder builder) {
      builder.decorator(OpenTelemetryService.newDecorator());
    }
  }

  public static class SuppressDecoratorAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean suppressDecorator(@Advice.Argument(0) Function<?, ?> decorator) {
      return decorator
          .getClass()
          .getName()
          .equals(
              "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$Decorator");
    }

    @Advice.OnMethodExit
    public static void handleSuppression(
        @Advice.This ServerBuilder builder,
        @Advice.Enter boolean suppressed,
        @Advice.Return(readOnly = false) ServerBuilder returned) {
      if (suppressed) {
        returned = builder;
      }
    }
  }
}
