/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.sysmsg.SystemMessage;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AkkaDefaultSystemMessageQueueInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("akka.dispatch.DefaultSystemMessageQueue"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("akka.dispatch.DefaultSystemMessageQueue");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("systemEnqueue")
            .and(takesArgument(0, named("akka.actor.ActorRef")))
            .and(takesArgument(1, named("akka.dispatch.sysmsg.SystemMessage"))),
        AkkaDefaultSystemMessageQueueInstrumentation.class.getName() + "$DispatchSystemAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchSystemAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enter(@Advice.Argument(1) SystemMessage systemMessage) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(systemMessage)) {
        ContextStore<SystemMessage, State> contextStore =
            InstrumentationContext.get(SystemMessage.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, systemMessage, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }
}
