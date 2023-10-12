/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.dispatch.Envelope;
import org.apache.pekko.dispatch.sysmsg.SystemMessage;

public class PekkoActorCellInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.ActorCell");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invoke").and(takesArgument(0, named("org.apache.pekko.dispatch.Envelope"))),
        PekkoActorCellInstrumentation.class.getName() + "$InvokeAdvice");
    transformer.applyAdviceToMethod(
        named("systemInvoke")
            .and(takesArgument(0, named("org.apache.pekko.dispatch.sysmsg.SystemMessage"))),
        PekkoActorCellInstrumentation.class.getName() + "$SystemInvokeAdvice");
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
