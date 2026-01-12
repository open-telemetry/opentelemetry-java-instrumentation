/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
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

public class SendCallbackInstrumentation implements TypeInstrumentation {

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
        isMethod().and(named("sendComplete")),
        SendCallbackInstrumentation.class.getName() + "$SendCallbackSendCompleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendCallbackSendCompleteAdvice {

    public static class AdviceScope {
      private final PulsarRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(PulsarRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(SendCallback callback) {
        // Extract the Context and PulsarRequest from the SendCallback instance.
        SendCallbackData callBackData = VirtualFieldStore.extract(callback);
        if (callBackData == null) {
          return null;
        }

        Context context = callBackData.context;
        return new AdviceScope(callBackData.request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable t) {
        // Close the Scope and end the span.
        scope.close();
        producerInstrumenter().end(context, request, null, t);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.This SendCallback callback) {
      return AdviceScope.start(callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Throwable t, @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(t);
      }
    }
  }
}
