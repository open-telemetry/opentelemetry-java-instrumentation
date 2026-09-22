/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.Consumer;

class SjmsConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.camel.component.sjms.SjmsConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createMessageHandler")
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.jms.Session")))
            .and(returns(named("javax.jms.MessageListener")))
            .and(isProtected()),
        getClass().getName() + "$CreateMessageHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateMessageHandlerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Consumer consumer, @Advice.Return @Nullable MessageListener messageListener) {
      if (messageListener != null
          && emitStableMessagingSemconv()
          && CamelInstrumentationEnabled.isEnabled(consumer.getEndpoint().getCamelContext())) {
        CamelJmsProcessingSelection.selectFrameworkProcessing(messageListener);
      }
    }
  }
}
