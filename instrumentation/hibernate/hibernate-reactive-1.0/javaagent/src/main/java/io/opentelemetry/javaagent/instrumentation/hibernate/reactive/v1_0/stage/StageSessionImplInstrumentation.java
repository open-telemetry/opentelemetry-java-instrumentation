/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.stage;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StageSessionImplInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.hibernate.reactive.stage.impl.StageSessionImpl",
        "org.hibernate.reactive.stage.impl.StageStatelessSessionImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("withTransaction")
            .and(takesArgument(0, Function.class).and(returns(CompletionStage.class))),
        this.getClass().getName() + "$WithTransactionAdvice");
  }

  @SuppressWarnings("unused")
  public static class WithTransactionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Function<?, ?> function) {
      function = FunctionWrapper.wrap(function);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) CompletionStage<?> completionStage) {
      completionStage = CompletionStageWrapper.wrap(completionStage);
    }
  }
}
