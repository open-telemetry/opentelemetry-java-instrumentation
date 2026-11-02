/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.impl.SendCallback;

class SendCallbackInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pulsar.client.impl.SendCallback");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.pulsar.client.impl.SendCallback"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Match both the public callback and the newer private single-argument completion fast path.
    transformer.applyAdviceToMethod(
        named("sendComplete")
            .and(takesArgument(0, namedOneOf("java.lang.Exception", "java.lang.Throwable"))),
        getClass().getName() + "$SendCallbackSendCompleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendCallbackSendCompleteAdvice {

    public static class AdviceScope {
      private final SendCallbackData data;
      private final Scope currentScope;

      private AdviceScope(SendCallbackData data, Scope currentScope) {
        this.data = data;
        this.currentScope = currentScope;
      }

      @Nullable
      public static AdviceScope start(SendCallback callback) {
        // Extract the operation from the SendCallback instance.
        SendCallbackData callbackData = VirtualFieldStore.takeSendCallbackData(callback);
        if (callbackData == null) {
          return null;
        }

        Context currentContext = callbackData.callbackCompletionContext();
        return new AdviceScope(callbackData, currentContext.makeCurrent());
      }

      public void end(@Nullable Throwable error) {
        currentScope.close();
        if (error != null) {
          data.end(error);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.This SendCallback callback) {
      return AdviceScope.start(callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable AdviceScope adviceScope, @Advice.Thrown @Nullable Throwable error) {
      if (adviceScope != null) {
        adviceScope.end(error);
      }
    }
  }
}
