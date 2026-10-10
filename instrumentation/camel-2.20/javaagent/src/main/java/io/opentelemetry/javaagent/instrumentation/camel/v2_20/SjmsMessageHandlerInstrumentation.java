/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelJmsProcessingOwnership.PROCESSING_STATE;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import javax.annotation.Nullable;
import javax.jms.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SjmsMessageHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    AgentDistributionConfig config = AgentDistributionConfig.get();
    if (config.isInstrumentationEnabled(
        asList("jms", "jms-1.1"), config.isInstrumentationDefaultEnabled())) {
      return none();
    }
    return named("org.apache.camel.component.sjms.consumer.AbstractMessageHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        getClass().getName() + "$MessageHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JmsMessageProcessingState onEnter(@Advice.Argument(0) Message message) {
      JmsMessageProcessingState state = PROCESSING_STATE.get(message);
      if (state == null || state.isProcessingCompleted()) {
        state = new JmsMessageProcessingState();
        PROCESSING_STATE.set(message, state);
      }
      state.beginProcessing();
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable JmsMessageProcessingState processingState) {
      if (processingState != null) {
        processingState.endProcessing();
      }
    }
  }
}
