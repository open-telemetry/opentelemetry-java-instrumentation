/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.dispatch.sysmsg.SystemMessage;

public class PekkoDefaultSystemMessageQueueInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.pekko.dispatch.DefaultSystemMessageQueue"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pekko.dispatch.DefaultSystemMessageQueue");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("systemEnqueue")
            .and(takesArgument(0, named("org.apache.pekko.actor.ActorRef")))
            .and(takesArgument(1, named("org.apache.pekko.dispatch.sysmsg.SystemMessage"))),
        PekkoDefaultSystemMessageQueueInstrumentation.class.getName() + "$DispatchSystemAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchSystemAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enter(@Advice.Argument(1) SystemMessage systemMessage) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, systemMessage)) {
        VirtualField<SystemMessage, PropagatedContext> virtualField =
            VirtualField.find(SystemMessage.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, systemMessage);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Argument(1) SystemMessage systemMessage,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      VirtualField<SystemMessage, PropagatedContext> virtualField =
          VirtualField.find(SystemMessage.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, virtualField, systemMessage);
    }
  }
}
