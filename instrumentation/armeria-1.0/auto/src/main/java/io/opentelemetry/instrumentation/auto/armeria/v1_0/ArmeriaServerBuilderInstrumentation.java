/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.armeria.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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

  // Intercept calls from app to register decorator and suppress them to avoid registering
  // multiple decorators, one from user app and one from our auto instrumentation. Otherwise, we
  // will end up with double telemetry.
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
  public static class SuppressDecoratorAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, inline = false)
    public static boolean suppressDecorator(@Advice.Argument(0) Function<?, ?> decorator) {
      return decorator != ArmeriaDecorators.SERVER_DECORATOR;
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
