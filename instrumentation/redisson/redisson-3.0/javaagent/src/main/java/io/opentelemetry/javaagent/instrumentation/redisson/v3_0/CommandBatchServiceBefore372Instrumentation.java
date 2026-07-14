/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.javaagent.instrumentation.redisson.v3_0.RedissonSingletons.batchInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchAdviceScope;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandBatchService;

class CommandBatchServiceBefore372Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.command.CommandBatchService")
        .and(not(declaresField(named("options"))))
        // Atomic execution was added in 3.6.0 with seven arguments and changed to one in 3.7.0.
        .and(declaresMethod(named("executeAsync").and(takesArguments(1).or(takesArguments(7)))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("async").and(returns(void.class)), getClass().getName() + "$CaptureAdvice");
    transformer.applyAdviceToMethod(
        named("executeAsync").and(takesArguments(1).or(takesArguments(7))),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This CommandBatchService service) {
      RedissonBatchAdviceScope.initialize(service);
    }
  }

  @SuppressWarnings("unused")
  public static class CaptureAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This CommandBatchService service,
        @Advice.Argument(2) Codec codec,
        @Advice.Argument(3) RedisCommand<?> command,
        @Advice.Argument(4) Object[] parameters) {
      RedissonBatchAdviceScope.captureCandidate(service, command, codec, parameters);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static RedissonBatchAdviceScope onEnter(
        @Advice.This CommandBatchService service, @Advice.AllArguments Object[] arguments) {
      Object options = arguments[arguments.length - 1];
      return RedissonBatchAdviceScope.start(service, options, batchInstrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable CompletionStage<?> result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable RedissonBatchAdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }
}
