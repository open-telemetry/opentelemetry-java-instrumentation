/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

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
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.pulsar.client.impl.SendCallback"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("sendComplete")).and(takesArgument(0, named("java.lang.Exception"))),
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
      SendCallBackData callBackData = VirtualFieldStore.extract(callback);
      if (callBackData != null && callBackData.request != null && callBackData.context != null) {
        // If the extraction was successful, store the Context and PulsarRequest in local variables.
        otelContext = callBackData.context;
        request = callBackData.request;
        otelScope = otelContext.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Exception e,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("otelRequest") PulsarRequest request) {
      if (otelScope != null && otelContext != null && request != null) {
        // Close the Scope and end the span.
        otelScope.close();
        producerInstrumenter().end(otelContext, request, null, e);
      }
    }
  }
}
