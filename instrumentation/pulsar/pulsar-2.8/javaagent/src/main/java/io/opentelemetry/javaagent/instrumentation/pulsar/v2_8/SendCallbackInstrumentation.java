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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This SendCallback callback,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("otelRequest") PulsarRequest request) {
      // Extract the Context and PulsarRequest from the SendCallback instance.
      SendCallbackData callBackData = VirtualFieldStore.extract(callback);
      if (callBackData != null) {
        // If the extraction was successful, store the Context and PulsarRequest in local variables.
        otelContext = callBackData.context;
        request = callBackData.request;
        otelScope = otelContext.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Throwable t,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("otelRequest") PulsarRequest request) {
      if (otelScope != null) {
        // Close the Scope and end the span.
        otelScope.close();
        producerInstrumenter().end(otelContext, request, null, t);
      }
    }
  }
}
