/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0.SpringJmsSingletons.isReceiveTelemetryEnabled;
import static io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0.SpringJmsSingletons.receiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JmsDestinationAccessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.jms.support.destination.JmsDestinationAccessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receiveFromConsumer").and(returns(named("javax.jms.Message"))),
        this.getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      if (isReceiveTelemetryEnabled()) {
        return null;
      }
      // suppress receive span creation in jms instrumentation
      Context context =
          InstrumenterUtil.suppressSpan(
              receiveInstrumenter(), Java8BytecodeBridge.currentContext(), null);
      return context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
    }
  }
}
