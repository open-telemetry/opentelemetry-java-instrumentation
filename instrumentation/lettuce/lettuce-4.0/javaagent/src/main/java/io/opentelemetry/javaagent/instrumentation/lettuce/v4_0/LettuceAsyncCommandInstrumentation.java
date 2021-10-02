/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.lambdaworks.redis.protocol.AsyncCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceAsyncCommandInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.protocol.AsyncCommand");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), LettuceAsyncCommandInstrumentation.class.getName() + "$SaveContextAdvice");
    transformer.applyAdviceToMethod(
        named("complete").or(named("completeExceptionally")).or(named("cancel")),
        LettuceAsyncCommandInstrumentation.class.getName() + "$RestoreContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class SaveContextAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void saveContext(@Advice.This AsyncCommand<?, ?, ?> asyncCommand) {
      Context context = Java8BytecodeBridge.currentContext();
      // get the context that submitted this command and attach it, it will be used to run callbacks
      context = context.get(LettuceSingletons.COMMAND_CONTEXT_KEY);
      VirtualField.find(AsyncCommand.class, Context.class).set(asyncCommand, context);
    }
  }

  @SuppressWarnings("unused")
  public static class RestoreContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This AsyncCommand<?, ?, ?> asyncCommand, @Advice.Local("otelScope") Scope scope) {
      Context context = VirtualField.find(AsyncCommand.class, Context.class).get(asyncCommand);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Local("otelScope") Scope scope) {
      scope.close();
    }
  }
}
