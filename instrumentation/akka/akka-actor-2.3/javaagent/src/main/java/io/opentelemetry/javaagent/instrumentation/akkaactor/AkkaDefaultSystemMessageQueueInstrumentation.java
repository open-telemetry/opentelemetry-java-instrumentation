/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.sysmsg.SystemMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
    public static PropagatedContext enter(@Advice.Argument(1) SystemMessage systemMessage) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, systemMessage)) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, VirtualFields.SYSTEM_MESSAGE_PROPAGATED_CONTEXT, systemMessage);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Argument(1) SystemMessage systemMessage,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext,
          throwable,
          VirtualFields.SYSTEM_MESSAGE_PROPAGATED_CONTEXT,
          systemMessage);
    }
  }
}
