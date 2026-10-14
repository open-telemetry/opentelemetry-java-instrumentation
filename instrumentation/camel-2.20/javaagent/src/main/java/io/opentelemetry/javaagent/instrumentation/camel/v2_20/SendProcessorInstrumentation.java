/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.Exchange;

class SendProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.camel.processor.SendProcessor",
        "org.apache.camel.processor.SendDynamicProcessor",
        "org.apache.camel.processor.Enricher",
        "org.apache.camel.processor.MulticastProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process").and(takesArguments(2)).and(returns(boolean.class)),
        getClass().getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Argument(0) Exchange exchange) {
      return ActiveContextManager.beginSend(exchange);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) Exchange exchange,
        @Advice.Return boolean completedSynchronously,
        @Advice.Enter Object state) {
      ActiveContextManager.endSend(exchange, state, !completedSynchronously);
    }
  }
}
