/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.javaagent.instrumentation.redisson.v3_17.RedissonSingletons.batchInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchAdviceScope;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchContext;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandBatchService;

class CommandBatchServiceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.command.CommandBatchService").and(declaresField(named("options")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(named("async"), getClass().getName() + "$CaptureAdvice");
    transformer.applyAdviceToMethod(named("executeAsync"), getClass().getName() + "$ExecuteAdvice");
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
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This CommandBatchService service,
        @Advice.Argument(2) Codec codec,
        @Advice.Argument(3) RedisCommand<?> command,
        @Advice.Argument(4) Object[] parameters,
        @Advice.FieldValue("options") Object options) {
      if (!RedissonBatchAdviceScope.capture(service, options, command, codec, parameters)) {
        return null;
      }
      return RedissonBatchContext.startCapture();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable CompletionStage<?> promise, @Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
        RedissonBatchContext.markFuture(promise);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static RedissonBatchAdviceScope onEnter(
        @Advice.This CommandBatchService service,
        @Advice.FieldValue("options") @Nullable Object options) {
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
