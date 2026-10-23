/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0.SpringJmsSingletons.RECEIVE_TELEMETRY_ENABLED;
import static io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0.SpringJmsSingletons.receiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsReceiveInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.springframework.jms.support.destination.JmsDestinationAccessor",
        "org.springframework.jms.listener.AbstractPollingMessageListenerContainer",
        "org.springframework.jms.listener.DefaultMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("receiveFromConsumer", "receiveMessage")
            .and(takesArgument(0, named("javax.jms.MessageConsumer"))),
        getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter() {
      if (RECEIVE_TELEMETRY_ENABLED) {
        return null;
      }
      // suppress receive span creation in jms instrumentation
      Context context =
          InstrumenterUtil.suppressSpan(
              receiveInstrumenter(), Context.current(), SpanKind.CONSUMER);
      return context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
    }
  }
}
