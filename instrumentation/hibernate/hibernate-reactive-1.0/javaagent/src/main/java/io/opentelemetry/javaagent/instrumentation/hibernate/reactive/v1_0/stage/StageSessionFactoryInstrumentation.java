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
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StageSessionFactoryInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.hibernate.reactive.stage.impl.StageSessionFactoryImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("withSession", "withStatelessSession").and(takesArgument(0, Function.class)),
        this.getClass().getName() + "$Function0Advice");
    transformer.applyAdviceToMethod(
        namedOneOf("withSession", "withStatelessSession").and(takesArgument(1, Function.class)),
        this.getClass().getName() + "$Function1Advice");
    transformer.applyAdviceToMethod(
        namedOneOf("openSession", "openStatelessSession").and(returns(CompletionStage.class)),
        this.getClass().getName() + "$OpenSessionAdvice");
  }

  @SuppressWarnings("unused")
  public static class Function0Advice {
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Function<?, ?> onEnter(@Advice.Argument(0) Function<?, ?> function) {
      return FunctionWrapper.wrap(function);
    }
  }

  @SuppressWarnings("unused")
  public static class Function1Advice {
    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Function<?, ?> onEnter(@Advice.Argument(1) Function<?, ?> function) {
      return FunctionWrapper.wrap(function);
    }
  }

  @SuppressWarnings("unused")
  public static class OpenSessionAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static CompletionStage<?> onExit(@Advice.Return CompletionStage<?> completionStage) {
      return CompletionStageWrapper.wrap(completionStage);
    }
  }
}
