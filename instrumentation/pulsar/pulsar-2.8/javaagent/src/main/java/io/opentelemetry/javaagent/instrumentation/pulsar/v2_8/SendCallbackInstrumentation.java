/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
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
    transformer.applyAdviceToMethod(
        named("sendComplete"), getClass().getName() + "$SendCallbackSendCompleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendCallbackSendCompleteAdvice {

    public static class AdviceScope {
      private final PulsarRequest request;
      private final Context currentContext;
      private final Scope currentScope;

      private AdviceScope(PulsarRequest request, Context currentContext, Scope currentScope) {
        this.request = request;
        this.currentContext = currentContext;
        this.currentScope = currentScope;
      }

      @Nullable
      public static AdviceScope start(SendCallback callback) {
        // Extract the Context and PulsarRequest from the SendCallback instance.
        SendCallbackData callbackData = VirtualFieldStore.takeSendCallbackData(callback);
        if (callbackData == null) {
          return null;
        }

        Context currentContext = callbackData.context;
        return new AdviceScope(callbackData.request, currentContext, currentContext.makeCurrent());
      }

      public void end(@Nullable Throwable t) {
        currentScope.close();
        producerInstrumenter().end(currentContext, request, null, t);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.This SendCallback callback) {
      return AdviceScope.start(callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) @Nullable Throwable t,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(t);
      }
    }
  }
}
