/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.Envelope;
import akka.dispatch.sysmsg.SystemMessage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.concurrent.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.concurrent.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AkkaActorCellInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.actor.ActorCell");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invoke").and(takesArgument(0, named("akka.dispatch.Envelope"))),
        AkkaActorCellInstrumentation.class.getName() + "$InvokeAdvice");
    transformer.applyAdviceToMethod(
        named("systemInvoke").and(takesArgument(0, named("akka.dispatch.sysmsg.SystemMessage"))),
        AkkaActorCellInstrumentation.class.getName() + "$SystemInvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.Argument(0) Envelope envelope) {
      VirtualField<Envelope, PropagatedContext> virtualField =
          VirtualField.find(Envelope.class, PropagatedContext.class);
      return TaskAdviceHelper.makePropagatedContextCurrent(virtualField, envelope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SystemInvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.Argument(0) SystemMessage systemMessage) {
      VirtualField<SystemMessage, PropagatedContext> virtualField =
          VirtualField.find(SystemMessage.class, PropagatedContext.class);
      return TaskAdviceHelper.makePropagatedContextCurrent(virtualField, systemMessage);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
