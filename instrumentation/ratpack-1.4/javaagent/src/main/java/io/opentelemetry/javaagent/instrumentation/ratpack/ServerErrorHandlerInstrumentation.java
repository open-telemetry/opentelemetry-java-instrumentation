/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.handling.Context;

public class ServerErrorHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("ratpack.error.ServerErrorHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract()).and(implementsInterface(named("ratpack.error.ServerErrorHandler")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("error")
            .and(takesArgument(0, named("ratpack.handling.Context")))
            .and(takesArgument(1, Throwable.class)),
        ServerErrorHandlerInstrumentation.class.getName() + "$ErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void captureThrowable(
        @Advice.Argument(0) Context ctx, @Advice.Argument(1) Throwable throwable) {
      Optional<io.opentelemetry.context.Context> otelContext =
          ctx.maybeGet(io.opentelemetry.context.Context.class);
      if (otelContext.isPresent()) {
        RatpackSingletons.onError(otelContext.get(), throwable);
      }
    }
  }
}
